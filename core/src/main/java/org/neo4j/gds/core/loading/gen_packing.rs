#!/usr/bin/env rust-script

// java sizes
const LONG: u32 = std::mem::size_of::<u64>() as _;
const INT: u32 = std::mem::size_of::<u32>() as _;
const SHORT: u32 = std::mem::size_of::<u16>() as _;
const BYTE: u32 = std::mem::size_of::<u8>() as _;

const LONG_BITS: u32 = u64::BITS;
const _INT_BITS: u32 = u32::BITS;
const _SHORT_BITS: u32 = u16::BITS;
const _BYTE_BITS: u32 = u8::BITS;

const DEFAULT_BLOCK_SIZE: u32 = 64;

const fn number_of_x_per_bits(block_size: u32, bits: u32, x: u32) -> u32 {
    (block_size * bits + x - 1) / x
}

const fn number_of_words_for_bits(block_size: u32, bits: u32) -> u32 {
    number_of_x_per_bits(block_size, bits, LONG_BITS)
}

const fn number_of_bytes_for_bits(block_size: u32, bits: u32) -> u32 {
    number_of_x_per_bits(block_size, bits, LONG)
}

const fn plural(n: u32) -> &'static str {
    if n == 1 {
        ""
    } else {
        "s"
    }
}

const PIN: &str = "values";
const OFF: &str = "valuesStart";
const PW: &str = "packedPtr";

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let block_size = std::env::args()
        .nth(1)
        .map(|arg| {
            arg.parse::<u32>()
                .map_err(|_| "block size must be a number")
        })
        .transpose()?
        .unwrap_or(DEFAULT_BLOCK_SIZE);

    if !block_size.is_power_of_two() {
        Err("block size must be a non-power of two")?;
    }
    if block_size > LONG_BITS {
        Err(format!("block size must be less than {}", LONG_BITS))?;
    }

    let packers = (0..=block_size).map(|i| pack(block_size, i)).collect();
    let unpackers = (0..=block_size).map(|i| unpack(block_size, i)).collect();
    let class = Class {
        documentation: vec![
            format!("This class is generated by {}", file!()),
            String::new(),
            "Do not edit this file directly.".into(),
        ],
        name: "AdjacencyPacking".into(),
        block_size,
        packers,
        unpackers,
    };
    let file = File {
        package: "org.neo4j.gds.core.loading".into(),
        class,
    };

    java::gen_file(file);

    Ok(())
}

#[derive(Copy, Clone)]
enum Primitive {
    Long,
    Int,
    Short,
    Byte,
}

impl Primitive {
    fn fits(self, bytes: u32) -> bool {
        match self {
            Primitive::Long => bytes >= LONG,
            Primitive::Int => bytes >= INT,
            Primitive::Short => bytes >= SHORT,
            Primitive::Byte => bytes >= BYTE,
        }
    }

    fn value(self) -> u32 {
        match self {
            Primitive::Long => LONG,
            Primitive::Int => INT,
            Primitive::Short => SHORT,
            Primitive::Byte => BYTE,
        }
    }

    fn find(bytes: u32) -> Primitive {
        const PRIMITIVES: [Primitive; 4] = [
            Primitive::Long,
            Primitive::Int,
            Primitive::Short,
            Primitive::Byte,
        ];

        *PRIMITIVES
            .iter()
            .find(|primitive| primitive.fits(bytes))
            .expect("no primitive fits the given number of bytes (=0)")
    }
}

enum Inst {
    Declare {
        word: u32,
    },
    DefineMask {
        constant: u64,
    },
    Pack {
        word: u32,
        offset: u32,
        shift: u32,
    },
    PackSplit {
        lower_word: u32,
        upper_word: u32,
        offset: u32,
        lower_shift: u32,
        upper_shift: u32,
    },
    Unpack {
        word: u32,
        offset: u32,
        shift: u32,
    },
    UnpackSplit {
        lower_word: u32,
        upper_word: u32,
        offset: u32,
        lower_shift: u32,
        upper_shift: u32,
    },
    Memset {
        size: u32,
        constant: u64,
    },
    Write {
        word: u32,
        offset: u32,
    },
    Read {
        word: u32,
        offset: u32,
    },
    Return {
        offset: u32,
    },
}

struct CodeBlock {
    comment: Option<String>,
    code: Vec<Inst>,
}

struct Method {
    documentation: Vec<String>,
    prefix: &'static str,
    bits: u32,
    code: Vec<CodeBlock>,
}

struct Class {
    documentation: Vec<String>,
    name: String,
    block_size: u32,
    packers: Vec<Method>,
    unpackers: Vec<Method>,
}

struct File {
    package: String,
    class: Class,
}

fn pack(block_size: u32, bits: u32) -> Method {
    let words = number_of_words_for_bits(block_size, bits);
    let bytes = number_of_bytes_for_bits(block_size, bits);

    let mut code = Vec::new();

    code.push(CodeBlock {
        comment: Some(format!("Touching {words} word{}", plural(words))),
        code: (0..words).map(|i| Inst::Declare { word: i }).collect(),
    });

    if bits != 0 {
        code.push(CodeBlock {
            comment: None,
            code: (0..block_size).map(|i| single_pack(bits, i)).collect(),
        });
    }

    let mut remaining = bytes;
    code.push(CodeBlock {
        comment: Some(format!("Write to {} byte{}", bytes, plural(bytes))),
        code: (0..words)
            .map(|word| {
                let bytes = Primitive::find(remaining);
                remaining -= bytes.value();

                let offset = word * LONG;

                Inst::Write { word, offset }
            })
            .collect(),
    });

    code.push(CodeBlock {
        comment: None,
        code: vec![Inst::Return { offset: bytes }],
    });

    Method {
        documentation: vec![format!(
            "Packs {block_size} {bits}-bit value{} into {bytes} byte{}, touching {words} word{}.",
            plural(block_size),
            plural(bytes),
            plural(words),
        )],
        prefix: "pack",
        bits,
        code,
    }
}

fn unpack(block_size: u32, bits: u32) -> Method {
    let words = number_of_words_for_bits(block_size, bits);
    let bytes = number_of_bytes_for_bits(block_size, bits);

    let mut code = Vec::new();

    code.push(CodeBlock {
        comment: Some(format!("Access {words} word{}", plural(words))),
        code: (0..words)
            .flat_map(|word| {
                [
                    Inst::Declare { word },
                    Inst::Read {
                        word,
                        offset: word * LONG,
                    },
                ]
            })
            .collect(),
    });

    if bits == 0 {
        code.push(CodeBlock {
            comment: None,
            code: vec![Inst::Memset {
                size: block_size,
                constant: 0,
            }],
        });
    } else {
        code.push(CodeBlock {
            comment: None,
            code: (0..block_size)
                .map(|i| {
                    let pack = single_pack(bits, i);
                    match pack {
                        Inst::Pack {
                            word,
                            offset,
                            shift,
                        } => Inst::Unpack {
                            word,
                            offset,
                            shift,
                        },
                        Inst::PackSplit {
                            lower_word,
                            upper_word,
                            offset,
                            lower_shift,
                            upper_shift,
                        } => Inst::UnpackSplit {
                            lower_word,
                            upper_word,
                            offset,
                            lower_shift,
                            upper_shift,
                        },
                        _ => unreachable!(),
                    }
                })
                .collect(),
        });

        if bits != block_size {
            let mask = (1_u64 << bits) - 1;

            code.last_mut()
                .unwrap()
                .code
                .insert(0, Inst::DefineMask { constant: mask });
        }
    }

    code.push(CodeBlock {
        comment: None,
        code: vec![Inst::Return { offset: bytes }],
    });

    Method {
        documentation: vec![format!(
            "Unpacks {block_size} {bits}-bit value{} using {bytes} byte{}, touching {words} word{}.",
            plural(block_size),
            plural(bytes),
            plural(words),
        )],
        prefix: "unpack",
        bits,
        code,
    }
}

fn single_pack(bits: u32, offset: u32) -> Inst {
    // how many bits we need to shift the current value to get to its position
    let shift = (offset * bits) % LONG_BITS;
    // the word for the lower bits of the current value
    let lower_word = offset * bits / LONG_BITS;
    // the word for the upper bits of the current value
    let upper_word = (offset * bits + bits - 1) / LONG_BITS;

    if lower_word == upper_word {
        // value fits within one word
        Inst::Pack {
            word: lower_word,
            offset,
            shift,
        }
    } else {
        // need to split the value across multiple words
        Inst::PackSplit {
            lower_word,
            upper_word,
            offset,
            lower_shift: shift,
            upper_shift: LONG_BITS - shift,
        }
    }
}

mod java {
    use super::*;

    const INDENT: &str = " ";
    const CLOSE: &str = "}";

    fn gen_method(method: Method) {
        println!("{INDENT:>4}/**");
        for doc in method.documentation {
            if doc.is_empty() {
                println!("{INDENT:>4} *");
            } else {
                println!("{INDENT:>4} * {doc}");
            }
        }
        println!("{INDENT:>4} */");
        println!(
            "{INDENT:>4}public static long {}{}(long[] {PIN}, int {OFF}, long {PW}) {{",
            method.prefix, method.bits
        );

        let mut mask = String::new();

        for code in method.code {
            if !code.code.is_empty() {
                if let Some(comment) = code.comment {
                    println!("{INDENT:>8}// {}", comment);
                }

                for inst in code.code {
                    match inst {
                        Inst::Declare { word } => {
                            println!("{INDENT:>8}long w{word};");
                        }
                        Inst::DefineMask { constant } => {
                            mask = format!(" & 0x{:X}L", constant);
                        }
                        Inst::Pack {
                            word,
                            offset,
                            shift,
                        } => {
                            let value = if offset == 0 {
                                format!("{PIN}[{OFF}]")
                            } else {
                                format!("{PIN}[{offset} + {OFF}]")
                            };

                            if shift == 0 {
                                println!("{INDENT:>8}w{word} = {value};");
                            } else {
                                println!("{INDENT:>8}w{word} |= {value} << {shift};");
                            }
                        }
                        Inst::PackSplit {
                            lower_word,
                            upper_word,
                            offset,
                            lower_shift,
                            upper_shift,
                        } => {
                            println!(
                                "{INDENT:>8}w{lower_word} |= {PIN}[{offset} + {OFF}] << {lower_shift};"
                            );
                            println!(
                                "{INDENT:>8}w{upper_word} = {PIN}[{offset} + {OFF}] >>> {upper_shift};"
                            );
                        }
                        Inst::Unpack {
                            word,
                            offset,
                            shift,
                        } => {
                            let value = if offset == 0 {
                                format!("{PIN}[{OFF}]")
                            } else {
                                format!("{PIN}[{offset} + {OFF}]")
                            };

                            if shift == 0 {
                                if method.bits == LONG_BITS {
                                    println!("{INDENT:>8}{value} = w{word};");
                                } else {
                                    println!("{INDENT:>8}{value} = w{word}{mask};");
                                }
                            } else {
                                if shift + method.bits == LONG_BITS {
                                    println!("{INDENT:>8}{value} = w{word} >>> {shift};");
                                } else {
                                    println!("{INDENT:>8}{value} = (w{word} >>> {shift}){mask};");
                                }
                            }
                        }
                        Inst::UnpackSplit {
                            lower_word,
                            upper_word,
                            offset,
                            lower_shift,
                            upper_shift,
                        } => {
                            println!(
                                "{INDENT:>8}{PIN}[{offset} + {OFF}] = ((w{lower_word} >>> {lower_shift}) | (w{upper_word} << {upper_shift})){mask};"
                            );
                        }
                        Inst::Memset { size, constant } => {
                            println!("{INDENT:>8}java.util.Arrays.fill({PIN}, {OFF}, {OFF} + {size}, 0x{constant:X}L);");
                        }
                        Inst::Write { word, offset } => {
                            let ptr = if offset == 0 {
                                format!("{PW}")
                            } else {
                                format!("{offset} + {PW}")
                            };

                            println!("{INDENT:>8}UnsafeUtil.putLong({ptr}, w{word});");
                        }
                        Inst::Read { word, offset } => {
                            let ptr = if offset == 0 {
                                format!("{PW}")
                            } else {
                                format!("{offset} + {PW}")
                            };
                            println!("{INDENT:>8}w{word} = UnsafeUtil.getLong({ptr});");
                        }
                        Inst::Return { offset } => {
                            if offset == 0 {
                                println!("{INDENT:>8}return {PW};");
                            } else {
                                println!("{INDENT:>8}return {offset} + {PW};");
                            }
                        }
                    }
                }
            }
        }

        println!("{:>4}{}", INDENT, CLOSE);
    }

    fn gen_class(class: Class) {
        println!("/**");
        for doc in class.documentation {
            if doc.is_empty() {
                println!(" *");
            } else {
                println!(" * {doc}");
            }
        }
        println!(" */");
        println!("public final class {} {{", class.name);
        println!();
        println!("{INDENT:>4}private {}() {{}}", class.name);
        println!();

        println!("{INDENT:>4}public static int advanceValueOffset(int {OFF}) {{");
        println!("{INDENT:>4}{INDENT:>4}return {OFF} + {};", class.block_size);
        println!("{INDENT:>4}{CLOSE}");
        println!();

        println!(
            "{INDENT:>4}public static long pack(int bits, long[] {PIN}, int {OFF}, long {PW}) {{"
        );
        println!(
            r#"{INDENT:>8}assert bits <= {bs} : "Bits must be at most {bs} but was " + bits;"#,
            bs = class.block_size
        );
        println!("{INDENT:>8}return PACKERS[bits].pack({PIN}, {OFF}, {PW});");
        println!("{INDENT:>4}{CLOSE}");
        println!();

        println!(
            "{INDENT:>4}public static long unpack(int bits, long[] {PIN}, int {OFF}, long {PW}) {{"
        );
        println!(
            r#"{INDENT:>8}assert bits <= {bs} : "Bits must be at most {bs} but was " + bits;"#,
            bs = class.block_size
        );
        println!("{INDENT:>8}return UNPACKERS[bits].unpack({PIN}, {OFF}, {PW});");
        println!("{INDENT:>4}{CLOSE}");
        println!();

        println!("{INDENT:>4}@FunctionalInterface");
        println!("{INDENT:>4}private interface Packer {{");
        println!("{INDENT:>4}{INDENT:>4}long pack(long[] {PIN}, int {OFF}, long {PW});");
        println!("{INDENT:>4}{CLOSE}");
        println!();

        println!("{INDENT:>4}@FunctionalInterface");
        println!("{INDENT:>4}private interface Unpacker {{");
        println!("{INDENT:>4}{INDENT:>4}long unpack(long[] {PIN}, int {OFF}, long {PW});");
        println!("{INDENT:>4}{CLOSE}");
        println!();

        println!("{INDENT:>4}private static final Packer[] PACKERS = {{");
        let (last, methods) = class.packers.split_last().unwrap();
        for method in methods {
            println!(
                "{INDENT:>8}{}::{}{},",
                class.name, method.prefix, method.bits
            );
        }
        println!("{INDENT:>8}{}::{}{},", class.name, last.prefix, last.bits);
        println!("{INDENT:>4}{CLOSE};");
        println!();

        println!("{INDENT:>4}private static final Unpacker[] UNPACKERS = {{");
        let (last, methods) = class.unpackers.split_last().unwrap();
        for method in methods {
            println!(
                "{INDENT:>8}{}::{}{},",
                class.name, method.prefix, method.bits
            );
        }
        println!("{INDENT:>8}{}::{}{},", class.name, last.prefix, last.bits);
        println!("{INDENT:>4}{CLOSE};");
        println!();

        for method in class.packers.into_iter().chain(class.unpackers) {
            gen_method(method);
            println!();
        }

        println!("{CLOSE}");
    }

    pub(super) fn gen_file(file: File) {
        println!(
            r#"/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package {};

import org.neo4j.internal.unsafe.UnsafeUtil;
"#,
            file.package
        );

        println!();
        gen_class(file.class);
    }
}

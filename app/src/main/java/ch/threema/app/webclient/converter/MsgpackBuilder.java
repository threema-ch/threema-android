/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.webclient.converter;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.annotation.SameThread;

@SameThread
public abstract class MsgpackBuilder {

	enum InstructionType {
		PACK_STRING,
		PACK_INTEGER,
		PACK_LONG,
		PACK_DOUBLE,
		PACK_FLOAT,
		PACK_BOOLEAN,
		PACK_BYTES,
		PACK_PAYLOAD,
		PACK_PAYLOAD_LIST,
	}

	interface Instruction {
		Object getValue();
		InstructionType getType();
	}

	final private List<Instruction> instructions = new LinkedList<>();
	private boolean consumed = false;

	final protected void build(MessageBufferPacker packer)
	{
		//write headers
		try {
			this
					.init(packer, this.instructions.size())
					//write contents
					.pack(packer);
		} catch (IOException e) {
			// This shouldn't happen, as we're writing to a buffer, not to a stream
			throw new RuntimeException("IOException while writing to MessageBufferPacker", e);
		}
	}

	final MsgpackBuilder addInstruction(Instruction instruction) {
		this.instructions.add(instruction);
		return this;
	}

	protected final int instructionCount() {
		return this.instructions.size();
	}

	abstract MsgpackBuilder init(MessageBufferPacker packer, int instructionSize) throws IOException;

	abstract MsgpackBuilder initInstruction(MessageBufferPacker packer, Instruction instruction) throws IOException;

	private MsgpackBuilder pack(MessageBufferPacker packer) {
		try {
			for (Instruction instruction : this.instructions) {
				this.initInstruction(packer, instruction);
				if (instruction.getValue() == null) {
					packer.packNil();
				} else {
					switch (instruction.getType()) {
						case PACK_STRING:
							packer.packString((String) instruction.getValue());
							break;
						case PACK_INTEGER:
							packer.packInt((Integer) instruction.getValue());
							break;
						case PACK_LONG:
							packer.packLong((Long) instruction.getValue());
							break;
						case PACK_DOUBLE:
							packer.packDouble((Double) instruction.getValue());
							break;
						case PACK_FLOAT:
							packer.packFloat((Float) instruction.getValue());
							break;
						case PACK_BOOLEAN:
							packer.packBoolean((Boolean) instruction.getValue());
							break;
						case PACK_BYTES:
							final byte[] bytes = (byte[]) instruction.getValue();
							packer.packBinaryHeader(bytes.length).writePayload(bytes);
							break;
						case PACK_PAYLOAD:
							((MsgpackBuilder) instruction.getValue()).build(packer);
							break;
						case PACK_PAYLOAD_LIST:
							List<MsgpackBuilder> list = (List<MsgpackBuilder>) instruction.getValue();
							packer.packArrayHeader(list.size());
							for (MsgpackBuilder builder: list) {
								builder.build(packer);
							}
							break;
					}
				}
			}
		} catch (IOException e) {
			// This shouldn't happen, as we're writing to a buffer, not to a stream
			throw new RuntimeException("IOException while writing to MessageBufferPacker", e);
		}

		return this;
	}


	@NonNull final public ByteBuffer consume(MessageBufferPacker packer) {
		if (this.consumed) {
			throw new RuntimeException("Builder has already been consumed!");
		}
		this.build(packer);
		this.consumed = true;
		return ByteBuffer.wrap(packer.toByteArray());
	}

	@NonNull final public ByteBuffer consume(MessagePack.PackerConfig config) {
		return this.consume(config.newBufferPacker());
	}

	@NonNull final public ByteBuffer consume() {
		return this.consume(new MessagePack.PackerConfig());
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.msgpack.core.MessageBufferPacker;

import java.io.IOException;
import java.util.List;

import ch.threema.annotation.SameThread;

/**
 * The MsgpackArrayBuilder allows to dynamically create a Msgpack array packet.
 *
 * Use the `put` method to insert new value. The insertion order will be preserved.
 *
 * You can also use another `MsgpackBuilder` instance as value.
 */
@SameThread
public class MsgpackArrayBuilder extends MsgpackBuilder {

	private static class Instruction implements MsgpackBuilder.Instruction {
		@NonNull
		private final InstructionType type;
		@Nullable
		private final Object value;

		@Nullable
		@Override
		public Object getValue() {
			return this.value;
		}

		@NonNull
		@Override
		public InstructionType getType() {
			return this.type;
		}

		Instruction(@NonNull InstructionType type, @Nullable Object value) {
			this.type = type;
			this.value = value;
		}
	}

	public MsgpackArrayBuilder put(@Nullable String value) {
		this.addInstruction(new Instruction(InstructionType.PACK_STRING, value));
		return this;
	}
	public MsgpackArrayBuilder put(@Nullable Integer value) {
		this.addInstruction(new Instruction(InstructionType.PACK_INTEGER, value));
		return this;
	}
	public MsgpackArrayBuilder put(@Nullable Long value) {
		this.addInstruction(new Instruction(InstructionType.PACK_LONG, value));
		return this;
	}
	public MsgpackArrayBuilder put(@Nullable Boolean value) {
		this.addInstruction(new Instruction(InstructionType.PACK_BOOLEAN, value));
		return this;
	}
	public MsgpackArrayBuilder put(@Nullable Double value) {
		this.addInstruction(new Instruction(InstructionType.PACK_DOUBLE, value));
		return this;
	}
	public MsgpackArrayBuilder put(@Nullable Float value) {
		this.addInstruction(new Instruction(InstructionType.PACK_FLOAT, value));
		return this;
	}
	public MsgpackArrayBuilder put( @Nullable byte[] value) {
		this.addInstruction(new Instruction(InstructionType.PACK_BYTES, value));
		return this;
	}
	public MsgpackArrayBuilder put(@NonNull MsgpackBuilder value) {
		this.addInstruction(new Instruction(InstructionType.PACK_PAYLOAD, value));
		return this;
	}
	public MsgpackArrayBuilder put(@NonNull List<MsgpackBuilder> values) {
		this.addInstruction(new Instruction(InstructionType.PACK_PAYLOAD_LIST, values));
		return this;
	}

	public MsgpackArrayBuilder maybePut(@Nullable String value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable Integer value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable Long value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable Float value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable Double value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable Boolean value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable byte[] value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable MsgpackBuilder value) {
		if (value != null) {
			this.put(value);
		}
		return this;
	}
	public MsgpackArrayBuilder maybePut(@Nullable List<MsgpackBuilder> values) {
		if (values != null) {
			this.put(values);
		}
		return this;
	}

	@Override
	MsgpackArrayBuilder init(MessageBufferPacker packer, int instructionSize) throws IOException {
		packer.packArrayHeader(instructionSize);
		return this;
	}

	@Override
	MsgpackArrayBuilder initInstruction(MessageBufferPacker packer, MsgpackBuilder.Instruction instruction) {
		//its a array, do nothing
		return this;
	}

	/**
	 * Return true if no values have been added to the builder.
	 */
	public boolean isEmpty() {
		return this.instructionCount() == 0;
	}
}

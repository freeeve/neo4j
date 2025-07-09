/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.protocol.io.reader;

import io.netty.buffer.Unpooled;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.neo4j.packstream.error.reader.PackstreamReaderException;
import org.neo4j.packstream.io.PackstreamBuf;
import org.neo4j.packstream.io.TypeMarker;
import org.neo4j.packstream.struct.StructHeader;
import org.neo4j.values.storable.Float32Vector;
import org.neo4j.values.storable.Float64Vector;
import org.neo4j.values.storable.Int16Vector;
import org.neo4j.values.storable.Int32Vector;
import org.neo4j.values.storable.Int8Vector;
import org.neo4j.values.storable.Values;
import org.neo4j.values.storable.VectorValue;

public class VectorReaderTest {

    private <V extends VectorValue> void assertValue(PackstreamBuf buf, Class<V> type, V expected)
            throws PackstreamReaderException {
        var value = VectorReader.getInstance().read(null, buf, new StructHeader(2, (short) 'V'));

        Assertions.assertThat(value)
                .asInstanceOf(InstanceOfAssertFactories.type(type))
                .satisfies(expected::equals);
    }

    @Test
    void shouldReadEmpty8Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.INT8.getValue()}))
                .writeBytes(Unpooled.EMPTY_BUFFER);

        this.assertValue(buffer, Int8Vector.class, Values.int8Vector());
    }

    @Test
    void shouldRead8Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.INT8.getValue()}))
                .writeBytes(Unpooled.buffer().writeByte(0).writeByte(42).writeByte(21));

        this.assertValue(buffer, Int8Vector.class, Values.int8Vector((byte) 0, (byte) 42, (byte) 21));
    }

    @Test
    void shouldReadEmpty16Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.INT16.getValue()}))
                .writeBytes(Unpooled.EMPTY_BUFFER);

        this.assertValue(buffer, Int16Vector.class, Values.int16Vector());
    }

    @Test
    void shouldRead16Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.INT16.getValue()}))
                .writeBytes(Unpooled.buffer().writeShort(0).writeShort(42).writeShort(21));

        this.assertValue(buffer, Int16Vector.class, Values.int16Vector((short) 0, (short) 42, (short) 21));
    }

    @Test
    void shouldReadEmpty32Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.INT32.getValue()}))
                .writeBytes(Unpooled.EMPTY_BUFFER);

        this.assertValue(buffer, Int32Vector.class, Values.int32Vector());
    }

    @Test
    void shouldRead32Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.INT32.getValue()}))
                .writeBytes(Unpooled.buffer().writeInt(0).writeInt(42).writeInt(21));

        this.assertValue(buffer, Int32Vector.class, Values.int32Vector(0, 42, 21));
    }

    @Test
    void shouldReadEmptyFloat32Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.FLOAT32.getValue()}))
                .writeBytes(Unpooled.EMPTY_BUFFER);

        this.assertValue(buffer, Float32Vector.class, Values.float32Vector());
    }

    @Test
    void shouldReadFloat32Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.FLOAT32.getValue()}))
                .writeBytes(
                        Unpooled.buffer().writeFloat(0.125f).writeFloat(42.25f).writeFloat(21.5f));

        this.assertValue(buffer, Float32Vector.class, Values.float32Vector(0.125f, 42.25f, 21.5f));
    }

    @Test
    void shouldReadEmptyFloat64Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.FLOAT64.getValue()}))
                .writeBytes(Unpooled.EMPTY_BUFFER);

        this.assertValue(buffer, Float64Vector.class, Values.float64Vector());
    }

    @Test
    void shouldReadFloat64Vector() throws PackstreamReaderException {
        var buffer = PackstreamBuf.allocUnpooled()
                .writeBytes(Unpooled.wrappedBuffer(new byte[] {(byte) TypeMarker.FLOAT64.getValue()}))
                .writeBytes(Unpooled.buffer()
                        .writeDouble(0.125f)
                        .writeDouble(42.25f)
                        .writeDouble(21.5f));

        this.assertValue(buffer, Float64Vector.class, Values.float64Vector(0.125f, 42.25f, 21.5f));
    }
}

/*
   Copyright 2019 Evan Saulpaugh

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.esaulpaugh.headlong.rlp;

import com.esaulpaugh.headlong.rlp.exception.DecodeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public final class RLPStream implements Iterable<RLPItem>, AutoCloseable {

    private final InputStream is;
    private final RLPDecoder decoder;

    public RLPStream(InputStream is) {
        this(is, RLPDecoder.RLP_STRICT);
    }

    public RLPStream(InputStream is, RLPDecoder decoder) {
        this.is = is;
        this.decoder = decoder;
    }

    @Override
    public Iterator<RLPItem> iterator() {
        return new RLPStreamIterator();
    }

    @Override
    public void close() throws IOException {
        is.close();
    }

    private final class RLPStreamIterator extends RLPIterator {

        RLPStreamIterator() {
            super(RLPStream.this.decoder, new byte[0], /* make sure index == buffer.length */ 0, Integer.MAX_VALUE);
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            try {
                final int available = is.available();
                if (available > 0) {
                    int keptBytes = buffer.length - index;
                    byte[] newBuffer = new byte[keptBytes + available];
                    System.arraycopy(buffer, index, newBuffer, 0, keptBytes);
                    buffer = newBuffer;
                    index = 0;
                    int read = is.read(buffer, keptBytes, available);
                    if (read != available) {
                        throw new IOException("read failed: " + read + " != " + available);
                    }
                }
            } catch (IOException io) {
                throw noSuchElementException(io);
            }
            if (index == buffer.length) {
                return false;
            }
            try {
                next = decoder.wrap(buffer, index);
                return true;
            } catch (DecodeException e) {
                if (e.isRecoverable()) {
                    return false;
                }
                throw RLPIterator.noSuchElementException(e);
            }
        }
    }
}
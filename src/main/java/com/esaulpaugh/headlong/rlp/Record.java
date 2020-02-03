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

import com.esaulpaugh.headlong.exception.DecodeException;
import com.esaulpaugh.headlong.exception.UnrecoverableDecodeException;
import com.esaulpaugh.headlong.util.Integers;
import com.esaulpaugh.headlong.util.Strings;

import java.nio.ByteBuffer;
import java.security.SignatureException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static com.esaulpaugh.headlong.rlp.RLPDecoder.RLP_STRICT;
import static com.esaulpaugh.headlong.util.Strings.BASE_64_URL_SAFE;

/** Implementation of EIP-778: Ethereum Node Records (ENR), https://eips.ethereum.org/EIPS/eip-778 */
public final class Record {

    private static final int MAX_RECORD_LEN = 300;

    private static final String ENR_PREFIX = "enr:";

    private final RLPList rlp;

    public Record(long seq, List<KeyValuePair> pairs, Signer signer) {
        final int signatureLen = signer.signatureLength();
        final int signatureItemLen = RLPEncoder.prefixLength(signatureLen) + signatureLen;
        final long payloadLenLong = rlpEncodedLen(seq) + RLPEncoder.dataLen(pairs);
        final long recordListPayloadLenLong = signatureItemLen + payloadLenLong;
        final int recordPrefixLen = RLPEncoder.prefixLength(recordListPayloadLenLong);
        final long recordLenLong = recordPrefixLen + recordListPayloadLenLong;
        if(recordLenLong > MAX_RECORD_LEN) {
            throw new IllegalArgumentException("record length exceeds maximum: " + recordLenLong + " > " + MAX_RECORD_LEN);
        }

        final int recordLen = (int) recordLenLong;
        ByteBuffer bb = ByteBuffer.allocate(recordLen);
        RLPEncoder.insertListPrefix((int) recordListPayloadLenLong, bb);
        final int contentListOffset = recordPrefixLen + signatureItemLen - RLPEncoder.prefixLength(payloadLenLong);
        bb.position(contentListOffset);
        RLPEncoder.insertRecordContentList((int) payloadLenLong, seq, pairs, bb);
        final byte[] signature = signer.sign(bb.array(), contentListOffset, recordLen - contentListOffset);
        bb.position(recordPrefixLen);
        RLPEncoder.insertRecordSignature(signature, bb);

        try {
            this.rlp = RLP_STRICT.wrapList(bb.array());
        } catch (DecodeException e) { // shouldn't happen if above code is correct
            throw new Error(e);
        }
    }

    private Record(RLPList recordRLP) {
        this.rlp = recordRLP;
    }

    public static Record parse(String enrString) throws DecodeException, ParseException {
        if(enrString.startsWith(ENR_PREFIX)) {
            return decode(Strings.decode(enrString.substring(ENR_PREFIX.length()), BASE_64_URL_SAFE));
        }
        throw new ParseException("prefix \"" + ENR_PREFIX + "\" not found", 0);
    }

    public static Record decode(byte[] record) throws DecodeException {
        return new Record(RLP_STRICT.wrapList(record));
    }

    public RLPList getRLP() {
        return rlp;
    }

    public RLPItem getSignature() throws DecodeException {
        try {
            return getRLP().iterator(RLP_STRICT).next();
        } catch (NoSuchElementException nsee) {
            throw new UnrecoverableDecodeException(nsee);
        }
    }

    public RLPList getContent() throws DecodeException {
        return RLP_STRICT.wrapList(getContentBytes(getSignature().endIndex));
    }

    public long getSeq() throws DecodeException {
        Iterator<RLPItem> iter = getRLP().iterator();
        iter.next();
        return iter.next().asLong();
    }

    private byte[] getContentBytes(int index) {
        int contentDataLen = rlp.encodingLength() - index;
        ByteBuffer bb = ByteBuffer.allocate(RLPEncoder.prefixLength(contentDataLen) + contentDataLen);
        RLPEncoder.insertListPrefix(contentDataLen, bb);
        rlp.exportRange(index, index + contentDataLen, bb.array(), bb.position());
        return bb.array();
    }

    public RLPList decode(Verifier verifier) throws DecodeException, SignatureException {
        RLPItem signatureItem = getSignature();
        byte[] content = getContentBytes(signatureItem.endIndex);
        verifier.verify(signatureItem.asBytes(), content); // verify content
        return RLPDecoder.RLP_STRICT.wrapList(content);
    }

    public interface Signer {
        int signatureLength();
        byte[] sign(byte[] message, int off, int len);
    }

    public interface Verifier {
        void verify(byte[] signature, byte[] content) throws SignatureException;
    }

    @Override
    public int hashCode() {
        return rlp.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return rlp.equals(((Record) o).rlp);
    }

    @Override
    public String toString() {
        return ENR_PREFIX + rlp.toString(BASE_64_URL_SAFE);
    }

    private static long rlpEncodedLen(long val) {
        int dataLen = Integers.len(val);
        if (dataLen == 1) {
            return (byte) val >= 0x00 ? 1 : 2;
        }
        return 1 + dataLen;
    }
}
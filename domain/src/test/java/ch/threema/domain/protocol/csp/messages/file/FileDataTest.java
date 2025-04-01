/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.file;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.BadMessageException;

public class FileDataTest {
    private static final byte[] blobIdBytes1 = new byte[ProtocolDefines.BLOB_ID_LEN];
    private static final byte[] blobIdBytes2 = new byte[ProtocolDefines.BLOB_ID_LEN];
    private static final byte[] encryptionKeyBytes = new byte[ProtocolDefines.BLOB_KEY_LEN];

    private static final String blobIdHex1;
    private static final String blobIdHex2;
    private static final String encryptionKeyHex;

    static {
        fillByteArray(blobIdBytes1, (byte) 0);
        fillByteArray(blobIdBytes2, (byte) 32);
        fillByteArray(encryptionKeyBytes, (byte) 0);

        blobIdHex1 = Utils.byteArrayToHexString(blobIdBytes1);
        blobIdHex2 = Utils.byteArrayToHexString(blobIdBytes2);
        encryptionKeyHex = Utils.byteArrayToHexString(encryptionKeyBytes);
    }

    private static void fillByteArray(@NonNull byte[] array, byte offset) {
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) (i + offset);
        }
    }

    private static final String testFile = "{"
        + "\"b\":\"" + blobIdHex1 + "\","
        + "\"t\":\"" + blobIdHex2 + "\","
        + "\"k\":\"" + encryptionKeyHex + "\","
        + "\"m\":\"image/jpg\","
        + "\"n\":\"testfile.jpg\","
        + "\"s\":123,"
        + "\"j\":0,"
        + "\"d\":\"this is a caption\""
        + "}";
    private static final String testFileCorrelation = "{"
        + "\"b\":\"" + blobIdHex1 + "\","
        + "\"t\":\"" + blobIdHex2 + "\","
        + "\"k\":\"" + encryptionKeyHex + "\","
        + "\"m\":\"image/jpg\","
        + "\"n\":\"testfile.jpg\","
        + "\"s\":123,"
        + "\"j\":0,"
        + "\"d\":\"this is a caption\","
        + "\"c\":\"1234567890\""
        + "}";

    private static final String testFileMetaData = "{"
        + "\"b\":\"" + blobIdHex1 + "\","
        + "\"t\":\"" + blobIdHex2 + "\","
        + "\"k\":\"" + encryptionKeyHex + "\","
        + "\"m\":\"image/jpg\","
        + "\"n\":\"testfile.jpg\","
        + "\"s\":123,"
        + "\"j\":0,"
        + "\"d\":\"this is a caption\","
        + "\"x\": {"
        + "\"a\": 1,"
        + "\"b\": 1.2,"
        + "\"c\": \"drei\""
        + "}"
        + "}";

    @Test
    public void parseValidString() {
        FileData result = null;
        try {
            result = FileData.parse(testFile);
        } catch (BadMessageException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(result);

        Assert.assertArrayEquals(blobIdBytes1, result.getFileBlobId());
        Assert.assertArrayEquals(blobIdBytes2, result.getThumbnailBlobId());
        Assert.assertArrayEquals(encryptionKeyBytes, result.getEncryptionKey());

        Assert.assertEquals("image/jpg", result.getMimeType());
        Assert.assertEquals("testfile.jpg", result.getFileName());
        Assert.assertEquals(123, result.getFileSize());
        Assert.assertNull(result.getCorrelationId());
        Assert.assertNull(result.getMetaData());
        Assert.assertEquals(FileData.RENDERING_DEFAULT, result.getRenderingType());

        result = null;
        try {
            result = FileData.parse(testFileCorrelation);
        } catch (BadMessageException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(result);

        Assert.assertArrayEquals(blobIdBytes1, result.getFileBlobId());
        Assert.assertArrayEquals(blobIdBytes2, result.getThumbnailBlobId());
        Assert.assertArrayEquals(encryptionKeyBytes, result.getEncryptionKey());

        Assert.assertEquals("image/jpg", result.getMimeType());
        Assert.assertEquals("testfile.jpg", result.getFileName());
        Assert.assertEquals(123, result.getFileSize());
        Assert.assertEquals("1234567890", result.getCorrelationId());
        Assert.assertNull(result.getMetaData());
        Assert.assertEquals(FileData.RENDERING_DEFAULT, result.getRenderingType());

        result = null;
        try {
            result = FileData.parse(testFileMetaData);
        } catch (BadMessageException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(result);

        Assert.assertArrayEquals(blobIdBytes1, result.getFileBlobId());
        Assert.assertArrayEquals(blobIdBytes2, result.getThumbnailBlobId());
        Assert.assertArrayEquals(encryptionKeyBytes, result.getEncryptionKey());

        Assert.assertEquals("image/jpg", result.getMimeType());
        Assert.assertEquals("testfile.jpg", result.getFileName());
        Assert.assertEquals(123, result.getFileSize());
        Assert.assertNull(result.getCorrelationId());
        Assert.assertNotNull(result.getMetaData());

        Assert.assertEquals(1, result.getMetaData().get("a"));
        Assert.assertEquals(1.2, result.getMetaData().get("b"));
        Assert.assertEquals("drei", result.getMetaData().get("c"));
        Assert.assertEquals(FileData.RENDERING_DEFAULT, result.getRenderingType());
    }


    @Test
    public void parseInvalidString() {
        try {
            FileData.parse("i want to be a hippie");
            Assert.fail("invalid string parsed");
        } catch (BadMessageException e) {
            //ok! exception received
        }
    }

    @Test
    public void generateStringTest() {
        FileData d = new FileData();
        d
            .setFileBlobId(FileDataTest.blobIdBytes1)
            .setThumbnailBlobId(FileDataTest.blobIdBytes2)
            .setEncryptionKey(FileDataTest.encryptionKeyBytes)
            .setMimeType("image/jpg")
            .setFileName("testfile.jpg")
            .setFileSize(123)
            .setRenderingType(FileData.RENDERING_DEFAULT)
            .setCaption("this is a caption")
            .setCorrelationId(null)
            .setMetaData(null)
        ;
        try {
            FileData b = FileData.parse(testFile);
            Assert.assertEquals(b.generateString(), d.generateString());
        } catch (BadMessageException e) {
            Assert.fail(e.getMessage());
        }

        d = new FileData();
        d
            .setFileBlobId(FileDataTest.blobIdBytes1)
            .setThumbnailBlobId(FileDataTest.blobIdBytes2)
            .setEncryptionKey(FileDataTest.encryptionKeyBytes)
            .setMimeType("image/jpg")
            .setFileName("testfile.jpg")
            .setFileSize(123)
            .setRenderingType(FileData.RENDERING_DEFAULT)
            .setCaption("this is a caption")
            .setCorrelationId("1234567890")
            .setMetaData(null)
        ;
        try {
            FileData b = FileData.parse(testFileCorrelation);
            Assert.assertEquals(b.generateString(), d.generateString());
        } catch (BadMessageException e) {
            Assert.fail(e.getMessage());
        }


        Map<String, Object> map = new HashMap<>();

        map.put("a", 1);
        map.put("b", 1.2);
        map.put("c", "drei");

        d = new FileData();
        d
            .setFileBlobId(FileDataTest.blobIdBytes1)
            .setThumbnailBlobId(FileDataTest.blobIdBytes2)
            .setEncryptionKey(FileDataTest.encryptionKeyBytes)
            .setMimeType("image/jpg")
            .setFileName("testfile.jpg")
            .setFileSize(123)
            .setRenderingType(FileData.RENDERING_DEFAULT)
            .setCaption("this is a caption")
            .setCorrelationId(null)
            .setMetaData(map)
        ;
        try {
            FileData b = FileData.parse(testFileMetaData);
            Assert.assertEquals(b.generateString(), d.generateString());
        } catch (BadMessageException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testEmptyCaptions() {
        ByteArrayOutputStream noCaptionOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream emptyCaptionOutput = new ByteArrayOutputStream();

        FileData fileDataWithoutCaption = getExampleFileDataWithoutCaption();

        FileData fileDataWithEmptyCaption = getExampleFileDataWithoutCaption();
        fileDataWithEmptyCaption.setCaption("");

        try {
            Assert.assertEquals(fileDataWithoutCaption.generateString(), fileDataWithEmptyCaption.generateString());

            fileDataWithoutCaption.write(noCaptionOutput);
            fileDataWithEmptyCaption.write(emptyCaptionOutput);
            Assert.assertArrayEquals(noCaptionOutput.toByteArray(), emptyCaptionOutput.toByteArray());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testBlankCaptions() {
        ByteArrayOutputStream noCaptionOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream blankCaptionOutput = new ByteArrayOutputStream();

        FileData fileDataWithBlankCaption = getExampleFileDataWithoutCaption();
        fileDataWithBlankCaption.setCaption(" \n");

        FileData fileDataWithoutCaption = getExampleFileDataWithoutCaption();
        try {
            Assert.assertEquals(fileDataWithoutCaption.generateString(), fileDataWithBlankCaption.generateString());

            fileDataWithoutCaption.write(noCaptionOutput);
            fileDataWithBlankCaption.write(blankCaptionOutput);
            Assert.assertArrayEquals(noCaptionOutput.toByteArray(), blankCaptionOutput.toByteArray());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    private FileData getExampleFileDataWithoutCaption() {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 1.2);
        map.put("c", "drei");

        FileData fileData = new FileData();
        fileData
            .setFileBlobId(FileDataTest.blobIdBytes1)
            .setThumbnailBlobId(FileDataTest.blobIdBytes2)
            .setEncryptionKey(FileDataTest.encryptionKeyBytes)
            .setMimeType("image/jpg")
            .setFileName("testfile.jpg")
            .setFileSize(123)
            .setRenderingType(FileData.RENDERING_DEFAULT)
            .setCorrelationId(null)
            .setMetaData(map);
        return fileData;
    }

}

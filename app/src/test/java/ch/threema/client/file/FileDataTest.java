/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client.file;

import ch.threema.client.BadMessageException;
import ch.threema.client.Utils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FileDataTest {
	private static final byte[] testByte1 = new byte[]{0x01,0x02,0x03,0x04};
	private static final byte[] testByte2 = new byte[]{0x01,0x02,0x03,0x04};
	private static final byte[] testByte3 = new byte[]{0x01,0x00,0x03,0x04};

	private static final String textByteHex1 = Utils.byteArrayToHexString(testByte1);
	private static final String textByteHex2 = Utils.byteArrayToHexString(testByte2);
	private static final String textByteHex3 = Utils.byteArrayToHexString(testByte3);

	private static final String testFile = "{"
			+"\"b\":\"" + textByteHex1 + "\","
			+"\"t\":\"" + textByteHex2 + "\","
			+"\"k\":\"" + textByteHex3 + "\","
			+"\"m\":\"image/jpg\","
			+"\"n\":\"testfile.jpg\","
			+"\"s\":123,"
			+"\"j\":0,"
			+"\"d\":\"this is a description\""
			+"}";
	private static final String testFileCorrelation = "{"
		+"\"b\":\"" + textByteHex1 + "\","
		+"\"t\":\"" + textByteHex2 + "\","
		+"\"k\":\"" + textByteHex3 + "\","
		+"\"m\":\"image/jpg\","
		+"\"n\":\"testfile.jpg\","
		+"\"s\":123,"
		+"\"j\":0,"
		+"\"d\":\"this is a description\","
		+"\"c\":\"1234567890\""
		+"}";

	private static final String testFileMetaData = "{"
		+"\"b\":\"" + textByteHex1 + "\","
		+"\"t\":\"" + textByteHex2 + "\","
		+"\"k\":\"" + textByteHex3 + "\","
		+"\"m\":\"image/jpg\","
		+"\"n\":\"testfile.jpg\","
		+"\"s\":123,"
		+"\"j\":0,"
		+"\"d\":\"this is a description\","
		+"\"x\": {"
			+ "\"a\": 1,"
			+ "\"b\": 1.2,"
			+ "\"c\": \"drei\""
			+"}"
		+"}";
	@Test
	public void parseValidString() {
		FileData result = null;
		try {
			result = FileData.parse(testFile);
		} catch (BadMessageException e) {
			Assert.fail(e.getMessage());
		}
		Assert.assertNotNull(result);

		Assert.assertTrue(Arrays.equals(testByte1, result.getFileBlobId()));
		Assert.assertTrue(Arrays.equals(testByte2, result.getThumbnailBlobId()));
		Assert.assertTrue(Arrays.equals(testByte3, result.getEncryptionKey()));

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

		Assert.assertTrue(Arrays.equals(testByte1, result.getFileBlobId()));
		Assert.assertTrue(Arrays.equals(testByte2, result.getThumbnailBlobId()));
		Assert.assertTrue(Arrays.equals(testByte3, result.getEncryptionKey()));

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

		Assert.assertTrue(Arrays.equals(testByte1, result.getFileBlobId()));
		Assert.assertTrue(Arrays.equals(testByte2, result.getThumbnailBlobId()));
		Assert.assertTrue(Arrays.equals(testByte3, result.getEncryptionKey()));

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
			.setFileBlobId(FileDataTest.testByte1)
			.setThumbnailBlobId(FileDataTest.testByte2)
			.setEncryptionKey(FileDataTest.testByte3)
			.setMimeType("image/jpg")
			.setFileName("testfile.jpg")
			.setFileSize(123)
			.setRenderingType(FileData.RENDERING_DEFAULT)
			.setDescription("this is a description")
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
			.setFileBlobId(FileDataTest.testByte1)
			.setThumbnailBlobId(FileDataTest.testByte2)
			.setEncryptionKey(FileDataTest.testByte3)
			.setMimeType("image/jpg")
			.setFileName("testfile.jpg")
			.setFileSize(123)
			.setRenderingType(FileData.RENDERING_DEFAULT)
			.setDescription("this is a description")
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
			.setFileBlobId(FileDataTest.testByte1)
			.setThumbnailBlobId(FileDataTest.testByte2)
			.setEncryptionKey(FileDataTest.testByte3)
			.setMimeType("image/jpg")
			.setFileName("testfile.jpg")
			.setFileSize(123)
			.setRenderingType(FileData.RENDERING_DEFAULT)
			.setDescription("this is a description")
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
}

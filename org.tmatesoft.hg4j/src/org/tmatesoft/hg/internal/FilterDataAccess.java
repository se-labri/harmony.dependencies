/*
 * Copyright (c) 2011-2012 TMate Software Ltd
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@hg4j.com
 */
package org.tmatesoft.hg.internal;

import java.io.IOException;


/**
 * XXX Perhaps, DataAccessSlice? Unlike FilterInputStream, we limit amount of data read from DataAccess being filtered.
 *   
 *   
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class FilterDataAccess extends DataAccess {
	private final DataAccess dataAccess;
	private final long offset;
	private final int length;
	private int count;

	public FilterDataAccess(DataAccess dataAccess, long offset, int length) {
		this.dataAccess = dataAccess;
		this.offset = offset;
		this.length = length;
		count = length;
	}

	protected int available() throws IOException {
		return count;
	}

	@Override
	public FilterDataAccess reset() throws IOException {
		dataAccess.reset();
		count = length;
		return this;
	}
	
	@Override
	public boolean isEmpty() throws IOException {
		return count <= 0;
	}
	
	@Override
	public int length() throws IOException {
		return length;
	}

	@Override
	public void seek(int localOffset) throws IOException {
		if (localOffset < 0 || localOffset > length) {
			throw new IllegalArgumentException();
		}
		dataAccess.longSeek(offset + localOffset);
		count = length - localOffset;
	}

	@Override
	public void skip(int bytes) throws IOException {
		int newCount = count - bytes;
		if (newCount < 0 || newCount > length) {
			throw new IllegalArgumentException();
		}
		seek(length - newCount);
		/*
		 can't use next code because don't want to rewind backing DataAccess on reset()
		 i.e. this.reset() modifies state of this instance only, while filtered DA may go further.
		 Only actual this.skip/seek/read would rewind it to desired position 
	  		dataAccess.skip(bytes);
			count = newCount;
		 */

	}

	@Override
	public byte readByte() throws IOException {
		if (count <= 0) {
			throw new IOException(String.format("Underflow. Bytes left: %d. FilterDA[offset: %d, length: %d]", count, offset, length));
		}
		if (count == length) {
			dataAccess.longSeek(offset);
		}
		count--;
		return dataAccess.readByte();
	}

	@Override
	public void readBytes(byte[] b, int off, int len) throws IOException {
		if (len == 0) {
			return;
		}
		if (count <= 0 || len > count) {
			throw new IOException(String.format("Underflow. Bytes left: %d, asked to read %d. FilterDA[offset: %d, length: %d]", count, len, offset, length));
		}
		if (count == length) {
			dataAccess.longSeek(offset);
		}
		dataAccess.readBytes(b, off, len);
		count -= len;
	}

	// done shall be no-op, as we have no idea what's going on with DataAccess we filter
}

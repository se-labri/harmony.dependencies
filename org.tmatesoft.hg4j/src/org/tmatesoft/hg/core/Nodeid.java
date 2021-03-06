/*
 * Copyright (c) 2010-2013 TMate Software Ltd
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
package org.tmatesoft.hg.core;

import static org.tmatesoft.hg.internal.DigestHelper.toHexString;

import java.util.Arrays;

import org.tmatesoft.hg.internal.DigestHelper;



/**
 * A 20-bytes (40 characters) long hash value to identify a revision.
 * @see http://mercurial.selenic.com/wiki/Nodeid
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 *
 */
public final class Nodeid implements Comparable<Nodeid> {

	/**
	 * Length of the nodeid in bytes
	 */
	public static final int SIZE = 20;

	/**
	 * Length of nodeid string representation, in bytes
	 */
	public static final int SIZE_ASCII = 40;

	/**
	 * <b>nullid</b>, empty root revision.
	 */
	public static final Nodeid NULL = new Nodeid(new byte[SIZE], false);

	private final byte[] binaryData; 

	/**
	 * @param binaryRepresentation - array of exactly 20 bytes
	 * @param shallClone - true if array is subject to future modification and shall be copied, not referenced
	 * @throws HgBadNodeidFormatException custom {@link IllegalArgumentException} subclass if supplied binary representation doesn't correspond to 20 bytes of sha1 digest 
	 */
	public Nodeid(byte[] binaryRepresentation, boolean shallClone) {
		// 5 int fields => 32 bytes
		// byte[20] => 48 bytes (16 bytes is Nodeid with one field, 32 bytes for byte[20] 
		if (binaryRepresentation == null || binaryRepresentation.length != SIZE) {
			throw new HgBadNodeidFormatException(String.format("Bad value: %s", String.valueOf(binaryRepresentation)));
		}
		/*
		 * byte[].clone() is not reflected when ran with -agentlib:hprof=heap=sites
		 * thus not to get puzzled why there are N Nodeids and much less byte[] instances,
		 * may use following code to see N byte[] as well.
		 *
		if (shallClone) {
			binaryData = new byte[20];
			System.arraycopy(binaryRepresentation, 0, binaryData, 0, 20);
		} else {
			binaryData = binaryRepresentation;
		}
		*/
		binaryData = shallClone ? binaryRepresentation.clone() : binaryRepresentation;
	}

	@Override
	public int hashCode() {
		return hashCode(binaryData);
	}
	
	/**
	 * Handy alternative to calculate hashcode without need to get {@link Nodeid} instance
	 * @param binaryNodeid array of exactly 20 bytes
	 * @return same value as <code>new Nodeid(binaryNodeid, false).hashCode()</code>
	 */
	public static int hashCode(byte[] binaryNodeid) {
		assert binaryNodeid.length == SIZE;
		// digest (part thereof) seems to be nice candidate for the hashCode
		byte[] b = binaryNodeid;
		return b[0] << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Nodeid) {
			return equalsTo(((Nodeid) o).binaryData);
		}
		return false;
	}

	public boolean equalsTo(byte[] buf) {
		return Arrays.equals(this.binaryData, buf);
	}
	
	public int compareTo(Nodeid o) {
		if (this == o) {
			return 0;
		}
		for (int i = 0; i < SIZE; i++) {
			if (binaryData[i] != o.binaryData[i]) {
				// if we need truly ascending sort, need to respect byte sign 
				// return (binaryData[i] & 0xFF) < (o.binaryData[i] & 0xFF) ? -1 : 1;
				// however, for our purposes partial sort is pretty enough
				return binaryData[i] < o.binaryData[i] ? -1 : 1;
			}
		}
		return 0;
	}

	/**
	 * Complete string representation of this Nodeid.
	 */
	@Override
	public String toString() {
		// XXX may want to output just single 0 for the NULL id?
		return toHexString(binaryData, 0, binaryData.length);
	}

	public String shortNotation() {
		return toHexString(binaryData, 0, 6);
	}
	
	public boolean isNull() {
		if (this == NULL) {
			return true;
		}
		for (int i = 0; i < SIZE; i++) {
			if (this.binaryData[i] != 0) {
				return false;
			}
		}
		return true;
	}

	// copy 
	public byte[] toByteArray() {
		return binaryData.clone();
	}

	/**
	 * Factory for {@link Nodeid Nodeids}.
	 * Primary difference with cons is handling of NULL id (this method returns constant) and control over array 
	 * duplication - this method always makes a copy of an array passed
	 * @param binaryRepresentation - byte array of a length at least offset + 20
	 * @param offset - index in the array to start from
	 * @throws HgBadNodeidFormatException custom {@link IllegalArgumentException} subclass when arguments don't select 20 bytes
	 */
	public static Nodeid fromBinary(byte[] binaryRepresentation, int offset) {
		if (binaryRepresentation == null || binaryRepresentation.length - offset < SIZE) {
			throw new HgBadNodeidFormatException(String.format("Bad value: %s", String.valueOf(binaryRepresentation)));
		}
		int i = 0;
		while (i < SIZE && binaryRepresentation[offset+i] == 0) i++;
		if (i == SIZE) {
			return NULL;
		}
		if (offset == 0 && binaryRepresentation.length == SIZE) {
			return new Nodeid(binaryRepresentation, true);
		}
		byte[] b = new byte[SIZE]; // create new instance if no other reasonable guesses possible
		System.arraycopy(binaryRepresentation, offset, b, 0, SIZE);
		return new Nodeid(b, false);
	}

	/**
	 * Parse encoded representation.
	 * 
	 * @param asciiRepresentation - encoded form of the Nodeid.
	 * @return object representation
	 * @throws HgBadNodeidFormatException custom {@link IllegalArgumentException} subclass when argument doesn't match encoded form of 20-bytes sha1 digest. 
	 */
	public static Nodeid fromAscii(String asciiRepresentation) throws HgBadNodeidFormatException {
		if (asciiRepresentation.length() != SIZE_ASCII) {
			throw new HgBadNodeidFormatException(String.format("Bad value: %s", asciiRepresentation));
		}
		// XXX is better impl for String possible?
		return fromAscii(asciiRepresentation.toCharArray(), 0, SIZE_ASCII);
	}
	
	/**
	 * Parse encoded representation. Similar to {@link #fromAscii(String)}.
	 * @throws HgBadNodeidFormatException custom {@link IllegalArgumentException} subclass when bytes are not hex digits or number of bytes != 40 (160 bits) 
	 */
	public static Nodeid fromAscii(byte[] asciiRepresentation, int offset, int length) throws HgBadNodeidFormatException {
		if (length != SIZE_ASCII) {
			throw new HgBadNodeidFormatException(String.format("Expected %d hex characters for nodeid, not %d", SIZE_ASCII, length));
		}
		try {
			byte[] data = new byte[SIZE];
			boolean zeroBytes = DigestHelper.ascii2bin(asciiRepresentation, offset, length, data);
			if (zeroBytes) {
				return NULL;
			}
			return new Nodeid(data, false);
		} catch (HgBadNodeidFormatException ex) {
			throw ex;
		} catch (IllegalArgumentException ex) {
			throw new HgBadNodeidFormatException(ex.getMessage());
		}
	}
	
	public static Nodeid fromAscii(char[] asciiRepresentation, int offset, int length) {
		byte[] b = new byte[length];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) asciiRepresentation[offset+i];
		}
		return fromAscii(b, 0, b.length);
	}
}

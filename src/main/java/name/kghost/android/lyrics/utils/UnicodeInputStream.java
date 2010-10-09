package name.kghost.android.lyrics.utils;

/**
 Original pseudocode   : Thomas Weidenfeller
 Implementation tweaked: Aki Nieminen

 http://www.unicode.org/unicode/faq/utf_bom.html
 BOMs:
 00 00 FE FF    = UTF-32, big-endian
 FF FE 00 00    = UTF-32, little-endian
 FE FF          = UTF-16, big-endian
 FF FE          = UTF-16, little-endian
 EF BB BF       = UTF-8

 Win2k Notepad:
 Unicode format = UTF-16LE
 ***/

import java.io.*;

/**
 * This inputstream will recognize unicode BOM marks and will skip bytes if
 * getEncoding() method is called before any of the read(...) methods.
 * 
 * Usage pattern: String enc = "ISO-8859-1"; // or NULL to use systemdefault
 * FileInputStream fis = new FileInputStream(file); UnicodeInputStream uin = new
 * UnicodeInputStream(fis, enc); enc = uin.getEncoding(); // check for BOM mark
 * and skip bytes InputStreamReader in; if (enc == null) in = new
 * InputStreamReader(uin); else in = new InputStreamReader(uin, enc);
 */
public class UnicodeInputStream extends Reader {
	private Reader internalIn;

	private static final int BOM_SIZE = 4;

	/**
	 * Read-ahead four bytes and check for BOM marks. Extra bytes are unread
	 * back to the stream, only BOM bytes are skipped.
	 * 
	 * @throws IOException
	 */
	public UnicodeInputStream(InputStream in, String defaultEnc)
			throws IOException {
		PushbackInputStream pin = new PushbackInputStream(in, BOM_SIZE);
		String encoding;

		byte bom[] = new byte[BOM_SIZE];
		int n, unread;
		n = pin.read(bom, 0, bom.length);

		if (n >= 3 && (bom[0] == (byte) 0xEF) && (bom[1] == (byte) 0xBB)
				&& (bom[2] == (byte) 0xBF)) {
			encoding = "UTF-8";
			unread = n - 3;
		} else if (n >= 4 && (bom[0] == (byte) 0x00) && (bom[1] == (byte) 0x00)
				&& (bom[2] == (byte) 0xFE) && (bom[3] == (byte) 0xFF)) {
			encoding = "UTF-32BE";
			unread = n - 4;
		} else if (n >= 4 && (bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)
				&& (bom[2] == (byte) 0x00) && (bom[3] == (byte) 0x00)) {
			encoding = "UTF-32LE";
			unread = n - 4;
		} else if (n >= 2 && (bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
			encoding = "UTF-16BE";
			unread = n - 2;
		} else if (n >= 2 && (bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
			encoding = "UTF-16LE";
			unread = n - 2;
		} else {
			encoding = defaultEnc;
			unread = n;
		}

		if (unread > 0)
			pin.unread(bom, (n - unread), unread);

		internalIn = new InputStreamReader(pin, encoding);
	}

	@Override
	public void close() throws IOException {
		internalIn.close();
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		return internalIn.read(cbuf, off, len);
	}
}

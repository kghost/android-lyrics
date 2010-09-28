package name.kghost.android.lyrics.store

import scala.xml.{ Elem, Text, XML }
import java.io.{ InputStream, InputStreamReader, IOException }
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.{ HttpStatus, HttpException, HttpClient, NameValuePair }

object QianQianService extends LyricsService {
  def find(info: LyricsSearchInfo): Seq[LyricsResultInfo] = {
    val client = new HttpClient()
    val method = new GetMethod("http://ttlrcct2.qianqian.com")
    try {
      method.setPath("/dll/lyricsvr.dll?sh")
      method.setQueryString(Array(
        new NameValuePair("Artist", encodeUTF16Hex(canonicalization(info.artist).toString)),
        new NameValuePair("Title", encodeUTF16Hex(canonicalization(info.track).toString)),
        new NameValuePair("Flags", "0")))

      if (client.executeMethod(method) != HttpStatus.SC_OK) {
        System.err.println("Method failed: " + method.getStatusLine())
      }

      parse(method.getResponseBodyAsStream)
    } catch {
      case ex: HttpException => { null }
      case ex: IOException => { null }
    }
    finally {
      method.releaseConnection()
    }
  }

  private object canonicalization extends Canonicalization
    with TrimAllSpace
    with ToLowerCase
    with HalfWidthKatakanaToFullWidth
    with FullWidthAsciiToHalfWidth

  private def encodeUTF16Hex(s: String): String = {
    val pre = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    val sb = new StringBuilder
    for (b: Byte <- s.getBytes("UTF-16LE"))
      sb.append(pre((b & 0xf0) / 0x10)).append(pre(b & 0xf))
    sb.toString
  }

  private def code(s: String, lrcId: Int): String = {
    var song: Array[Int] = for (b <- s.getBytes("UTF-8")) yield b.toInt & 0xff
    var t1: Int = 0
    var t2: Int = 0
    var t3: Int = 0;
    t1 = (lrcId & 0x0000FF00) >> 8;
    if ((lrcId & 0x00FF0000) == 0) {
      t3 = 0x000000FF & ~t1;
    } else {
      t3 = 0x000000FF & ((lrcId & 0x00FF0000) >> 16);
    }

    t3 = t3 | ((0x000000FF & lrcId) << 8);
    t3 = t3 << 8;
    t3 = t3 | (0x000000FF & t1);
    t3 = t3 << 8;
    if ((lrcId & 0xFF000000) == 0) {
      t3 = t3 | (0x000000FF & (~lrcId));
    } else {
      t3 = t3 | (0x000000FF & (lrcId >> 24));
    }

    var j: Int = song.length - 1;
    while (j >= 0) {
      var c: Int = song(j);
      if (c >= 0x80) c = c - 0x100;

      t1 = ((c + t2) & 0x00000000FFFFFFFF);
      t2 = ((t2 << (j % 2 + 4)) & 0x00000000FFFFFFFF);
      t2 = ((t1 + t2) & 0x00000000FFFFFFFF);
      j -= 1;
    }
    j = 0;
    t1 = 0;
    while (j <= song.length - 1) {
      var c: Int = song(j);
      if (c >= 128) c = c - 256;
      var t4: Int = ((c + t1) & 0x00000000FFFFFFFF);
      t1 = ((t1 << (j % 2 + 3)) & 0x00000000FFFFFFFF);
      t1 = ((t1 + t4) & 0x00000000FFFFFFFF);
      j += 1;
    }

    var t5: Int = Conv(t2 ^ t3).toInt;
    t5 = Conv(t5 + (t1 | lrcId)).toInt;
    t5 = Conv(t5 * (t1 | t3)).toInt;
    t5 = Conv(t5 * (t2 ^ lrcId)).toInt;

    var t6: Long = t5;
    if (t6 > 2147483648l)
      t5 = (t6 - 4294967296l).toInt;
    return t5.toString;
  }

  private def Conv(i: Int): Long = {
    var r: Long = i % 4294967296l;
    if (i >= 0 && r > 2147483648l)
      r = r - 4294967296l;

    if (i < 0 && r < 2147483648l)
      r = r + 4294967296l;
    return r;
  }

  private case class LyricsResult(id: String, artist: String, album: String, track: String) extends LyricsResultInfo {
    def getResult: ILyrics = {
      val client = new HttpClient()
      val method = new GetMethod("http://ttlrcct2.qianqian.com")
      try {
        method.setPath("/dll/lyricsvr.dll?dl")
        method.setQueryString(Array(
          new NameValuePair("Id", id),
          new NameValuePair("Code", code(artist + track, augmentString(id).toInt))))

        if (client.executeMethod(method) != HttpStatus.SC_OK) {
          println("Method failed: " + method.getStatusLine())
        }

        (new LrcParser).parse(method.getResponseBodyAsStream)
      } catch {
        case e: HttpException => { null }
        case e: IOException => { null }
      }
      finally {
        method.releaseConnection()
      }
    }
  }

  private def parse(response: InputStream): Seq[LyricsResultInfo] = {
    XML.load(new InputStreamReader(response, "UTF-8")) match {
      case <result>{ lrcs@_* }</result> =>
        for (lyrics@ <lrc/> <- lrcs)
          yield new LyricsResult((lyrics \ "@id").text, (lyrics \ "@artist").text, "Unknown album", (lyrics \ "@title").text)
    }
  }
}

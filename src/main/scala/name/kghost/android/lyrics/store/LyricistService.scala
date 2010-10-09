package name.kghost.android.lyrics.store

import name.kghost.android.lyrics.utils.UnicodeInputStream
import name.kghost.android.lyrics.R
import scala.xml.{ Elem, NamespaceBinding, Text, XML }
import java.io.{ InputStream, InputStreamReader, IOException }
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.{ HttpStatus, HttpException, HttpClient, NameValuePair }

class LyricistService extends LyricsService { srv =>
  override def provider = R.drawable.provider_lyricist
  override def name = "Lyricist"
  override def find(info: LyricsSearchInfo): Seq[LyricsResultInfo] = {
    val client = new HttpClient()
    val method = new GetMethod("http://www.winampcn.com/lrceng/get.aspx")
    try {
      method.setQueryString(Array(
        new NameValuePair("song", canonicalization(info.track).toString),
        new NameValuePair("artist", canonicalization(info.artist).toString),
        new NameValuePair("lsong", canonicalization(info.track).toString),
        new NameValuePair("prec", "1"),
        new NameValuePair("Datetime", "20060601")))

      if (client.executeMethod(method) == HttpStatus.SC_OK)
        parse(method.getResponseBodyAsStream)
      else
        null
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
    with TrimAllMarks
    with HalfWidthKatakanaToFullWidth
    with FullWidthAsciiToHalfWidth

  private case class LyricsResult(url: String, artist: String, album: String, track: String) extends LyricsResultInfo {
    override def provider = srv.provider
    override def hasTimeline = true
    def getResult: ILyrics = {
      val client = new HttpClient()
      val method = new GetMethod(url)
      try {
        if (client.executeMethod(method) == HttpStatus.SC_OK)
          LrcParser.parse(method.getResponseBodyAsStream, this, { (x, y) => x })
        else
          null
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
    XML.load(new UnicodeInputStream(response, "UTF-8")) match {
      case <Lyric>{ lrcs@_* }</Lyric> =>
        for (Elem(null, "LyricUrl", attributes, NamespaceBinding(null, null, null), child) <- lrcs) yield {
          new LyricsResult(child.text, attributes("Artist").text, attributes("album").text, attributes("SongName").text)
        }
    }
  }
}
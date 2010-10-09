package name.kghost.android.lyrics.store

import android.database.{ Cursor, SQLException }
import android.database.sqlite.{ SQLiteDatabase, SQLiteOpenHelper }
import android.content.{ Context, ContentValues }
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream }
import scala.collection.Seq
import name.kghost.android.lyrics.utils.With
import name.kghost.android.lyrics.R

class LocalStorageService(context: Context) extends LyricsService { srv =>
  private val sql = new SQLiteOpenHelper(context, "lyrics", null, 1) {
    private final val TABLE_CREATE: String =
      """CREATE TABLE "lyrics" ("id" INTEGER NOT NULL, "artist" TEXT NOT NULL ,""" +
        """ "album" TEXT NOT NULL , "track" TEXT NOT NULL , "type" INTEGER NOT NULL ,""" +
        """ "lyrics_artist" TEXT NOT NULL , "lyrics_album" TEXT NOT NULL ,""" +
        """ "lyrics_track" TEXT NOT NULL , "lyrics" BLOB NOT NULL , PRIMARY KEY ("id"));""" +
        """CREATE INDEX "search" ON "lyrics" ("artist" ASC, "album" ASC, "track" ASC);"""
    override def onCreate(db: SQLiteDatabase): Unit = db.execSQL(TABLE_CREATE)
    override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = Unit
  }

  private def deserializeTimeline(blob: Array[Byte]): Seq[(Int, Int, String)] = {
    val s = new DataInputStream(new ByteArrayInputStream(blob))
    try {
      deserializeTimeline(s, s.readInt, Nil)
    } finally {
      s.close
    }
  }
  private def deserializeTimeline(s: DataInputStream, remain: Int, list: List[(Int, Int, String)]): Seq[(Int, Int, String)] =
    if (remain > 0)
      deserializeTimeline(s, remain - 1, (s.readInt, s.readInt, s.readUTF) :: list)
    else
      list

  private case class LocalLyrics(id: Int, artist: String, album: String, track: String, lyrics: String) extends ILyrics
  private case class LocalLyricsWithTimeline(id: Int, artist: String, album: String, track: String, timeline: Seq[(Int, Int, String)])
    extends ILyricsWithTimeline

  private def getResult(cursor: Cursor, list: List[LyricsResultInfo]): Seq[LyricsResultInfo] = {
    if (cursor.moveToNext)
      getResult(cursor, new LyricsResultInfo {
        private val typo = cursor.getInt(1)
        private val blob = cursor.getBlob(5)
        override def provider = srv.provider
        override def hasTimeline = typo == 2
        override val artist = cursor.getString(2)
        override val album = cursor.getString(3)
        override val track = cursor.getString(4)
        private val id = cursor.getInt(0)
        override def getResult = typo match {
          case 1 => LocalLyrics(id, artist, album, track, new String(blob, "UTF-8"))
          case 2 => LocalLyricsWithTimeline(id, artist, album, track, deserializeTimeline(blob))
        }
      } :: list)
    else
      list
  }

  override def provider = R.drawable.provider_local
  override def name = "Local"
  override def find(info: LyricsSearchInfo): Seq[LyricsResultInfo] =
    {
      val db = sql.getReadableDatabase
      try {
        val cursor = db.query("lyrics", Array("id", "type", "lyrics_artist", "lyrics_album",
          "lyrics_track", "lyrics"), """"artist" = ? AND "album" = ? AND "track" = ?""",
          Array(info.artist, info.album, info.track), null, null, null)
        try {
          getResult(cursor, Nil)
        } finally {
          cursor.close()
        }
      } finally {
        db.close
      }
    }

  private def serializeTimeline(list: Seq[(Int, Int, String)]): Array[Byte] = {
    val r = new ByteArrayOutputStream
    val s = new DataOutputStream(r)
    s.writeInt(list.length)
    list.reverse foreach {
      case (x, y, z) => s.writeInt(x); s.writeInt(y); s.writeUTF(z)
    }
    r.toByteArray
  }

  case class AlreadySavedException extends Exception("Lyrics already saved")
  case class NotSavedException extends Exception("Lyrics not saved")

  private def save(info: LyricsSearchInfo, lyrics: ILyrics)(saveExtra: (ContentValues) => Unit): Unit = {
    val db = sql.getWritableDatabase
    try {
      if (0 > db.insertOrThrow("lyrics", null, With(new ContentValues) { v =>
        v.put("artist", info.artist)
        v.put("album", info.album)
        v.put("track", info.track)
        v.put("lyrics_artist", lyrics.artist)
        v.put("lyrics_album", lyrics.album)
        v.put("lyrics_track", lyrics.track)
        saveExtra(v)
      })) throw new SQLException("Unknown")
    } finally {
      db.close
    }
  }

  def saveLyricsWithTimeline(info: LyricsSearchInfo, lyrics: ILyricsWithTimeline): Unit = lyrics match {
    case l: LocalLyricsWithTimeline => throw AlreadySavedException()
    case l =>
      save(info, lyrics) { v =>
        v.put("type", 2: Integer)
        v.put("lyrics", serializeTimeline(lyrics.timeline))
      }
  }

  def saveLyrics(info: LyricsSearchInfo, lyrics: ILyrics): Unit = lyrics match {
    case l: LocalLyrics => throw AlreadySavedException()
    case l =>
      save(info, lyrics) { v =>
        v.put("type", 1: Integer)
        v.put("lyrics", lyrics.lyrics.getBytes("UTF-8"))
      }
  }

  private def delete(id: Int): Unit = {
    val db = sql.getWritableDatabase
    try {
      if (0 > db.delete("lyrics", "id = ?", Array(id.toString)))
        throw new SQLException("Unknown")
    } finally {
      db.close
    }
  }

  def deleteLyrics(lyrics: ILyrics): Unit = lyrics match {
    case l: LocalLyrics => delete(l.id)
    case l: LocalLyricsWithTimeline => delete(l.id)
    case l => throw NotSavedException()
  }
}

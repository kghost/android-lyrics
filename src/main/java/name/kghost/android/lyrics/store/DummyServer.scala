package name.kghost.android.lyrics.store

object DummyServer extends LyricsService {
  override def find(info: LyricsSearchInfo): List[LyricsResultInfo] = new LyricsResultInfo {
    val artist = "ArtisT"
    val album = "AlbuM"
    val track = "TracK"
    override def getResult = Lyrics("ArtisT", "AlbuM", "TracK", "First Line\nSecond Line\nLast Line")
  } :: new LyricsResultInfo {
    val artist = "ArtisT"
    val album = "AlbuM"
    val track = "TracK Long"
    override def getResult = Lyrics("ArtisT", "AlbuM", "TracK Long",
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n" +
      "First Line\nSecond Line\nLast Line\n")
  } :: new LyricsResultInfo {
    val artist = "ArtisT"
    val album = "AlbuM"
    val track = "TracK Timeline"
    override def getResult = LyricsWithTimeline("ArtisT2", "AlbuM2", "TracK Timeline",
      (1, 2, "First Line2") :: (2, 4, "Second Line2") :: List((5, 10, "Last Line2")))
  } :: List(new LyricsResultInfo {
    val artist = "ArtisT"
    val album = "AlbuM"
    val track = "TracK Timeline Long"
    override def getResult = LyricsWithTimeline("ArtisT2", "AlbuM2", "TracK Timeline Long",
      (1, 2, "First Line2") :: (2, 4, "Second Line2") :: (5, 10, "Last Line2") :: (1, 2, "First Line2") :: (2, 4, "Second Line2") :: (5, 10, "Last Line2") :: (1, 2, "First Line2") :: (2, 4, "Second Line2") :: (5, 10, "Last Line2") :: (1, 2, "First Line2") :: (2, 4, "Second Line2") :: (5, 10, "Last Line2") :: (1, 2, "First Line2") :: (2, 4, "Second Line2") :: (5, 10, "Last Line2") :: (1, 2, "First Line2") :: (2, 4, "Second Line2") :: (5, 10, "Last Line2") :: (1, 2, "First Line2") :: (2, 4, "Second Line2") :: (5, 10, "Last Line2") :: (1, 2, "First Line2") :: (2, 4, "Second Line2") :: List((5, 10, "Last Line2")))
  })
}

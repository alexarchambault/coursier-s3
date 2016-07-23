package coursier.cache.protocol

import java.io.{File, PrintWriter}
import java.net.URL
import java.nio.charset.CodingErrorAction
import java.nio.file.{Files, Path}
import java.util.stream.Collectors

import awscala.s3.Bucket
import utest._

import scala.collection.JavaConversions._
import scala.io.{Codec, Source}
import scala.util.Try

object S3cHandlerTest extends TestSuite {

  implicit val codec = Codec("UTF-8")
  codec.onMalformedInput(CodingErrorAction.REPLACE)
  codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

  val tests = withTmpDir { tmpPath =>

    S3Client.setEndpoint("http://localhost:4568")

    val bucket = Bucket("test-s3-coursier-com")

    val directory = tmpPath.resolve("directory").toFile
    directory.mkdirs()
    val file1 = tmpPath.resolve("file1.jar").toFile
    val file2 = tmpPath.resolve("directory/file2.jar").toFile
    val file3 = tmpPath.resolve("directory/file3.jar").toFile

    write(file1, "File 1")
    write(file2, "File 2")
    write(file3, "File 3")

    S3Client.instance.put(bucket, "file1.jar", file1)
    S3Client.instance.put(bucket, "directory/file2.jar", file2)
    S3Client.instance.put(bucket, "directory/file3.jar", file3)

    S3cHandler.setupS3Handler()
    val s3cHandler = new S3cHandler()

    this {
      'test1 {
        val file1Url = new URL("s3c://localhost:4568/test-s3-coursier-com/file1.jar")
        val is = s3cHandler.openConnection(file1Url).getInputStream

        val lines = Source.fromInputStream(is).getLines().toList

        is.close()

        assert(lines == List("File 1"))
      }
      'test2 {
        val directoryUrl = new URL("s3c://localhost:4568/test-s3-coursier-com/directory/")
        val is = s3cHandler.openConnection(directoryUrl).getInputStream

        val htmlBody =
          s"""<html>
              |<head>
              |</head>
              |<body>
              |<pre><a href=":file2.jar">file2.jar</a></pre>
              |<pre><a href=":file3.jar">file3.jar</a></pre>
              |</body>
              |</html>""".stripMargin.split("\n").toList

        val lines = Source.fromInputStream(is).getLines().toList

        is.close()

        assert(lines == htmlBody)
      }
      'test3 {
        val directoryUrl = new URL("s3c://localhost:4568/test-s3-coursier-com/directory/file2.jar")
        val is = s3cHandler.openConnection(directoryUrl).getInputStream

        val lines = Source.fromInputStream(is).getLines().toList

        is.close()

        assert(lines == List("File 2"))
      }
      'test4 {
        val directoryUrl = new URL("s3c://localhost:4568/test-s3-coursier-com/directory/file3.jar")
        val is = s3cHandler.openConnection(directoryUrl).getInputStream

        val lines = Source.fromInputStream(is).getLines().toList

        is.close()

        assert(lines == List("File 3"))
      }
    }

  }

  private def write(file: File, content: String) = {
    val writer = new PrintWriter(file)
    writer.write(content)
    writer.close()
  }

  private def withTmpDir[T](block: Path => T): T = {
    val tmpPath = Files.createTempDirectory("coursier-s3")

    val res = block(tmpPath)

    Files.walk(tmpPath)
      .collect(Collectors.toList())
      .map(_.toFile)
      .sortBy(_.isDirectory)
      .foreach(f => Try(Files.delete(f.toPath)))

    Try(Files.delete(tmpPath))

    res
  }
}

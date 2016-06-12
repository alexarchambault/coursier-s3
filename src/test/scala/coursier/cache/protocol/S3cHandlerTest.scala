package coursier.cache.protocol

import java.io.{File, PrintWriter}
import java.net.URL
import java.nio.charset.CodingErrorAction
import java.nio.file.Files

import awscala.s3.Bucket
import utest._

import scala.io.{Codec, Source}

object S3cHandlerTest extends TestSuite {

  implicit val codec = Codec("UTF-8")
  codec.onMalformedInput(CodingErrorAction.REPLACE)
  codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

  S3cHandler.setupS3Handler()

  val bucket = Bucket("test.coursier-s3.com")

  val tmpPath = Files.createTempDirectory("coursier-s3")
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

  val s3cHandler = new S3cHandler()

  val tests = this {
    'test1 {
      val file1Url = new URL("s3c://localhost:4568/test.coursier-s3.com/file1.jar")
      val is = s3cHandler.openConnection(file1Url).getInputStream

      assert(Source.fromInputStream(is).getLines().toList == List("File 1"))
    }
    'test2 {
      val directoryUrl = new URL("s3c://localhost:4568/test.coursier-s3.com/directory/")
      val is = s3cHandler.openConnection(directoryUrl).getInputStream

      val htmlBody =
        s"""
           |<html>
           |<head>
           |</head>
           |<body>
           |<pre><a href=":directory/file2.jar">directory/file2.jar</a></pre>
           |<pre><a href=":directory/file3.jar">directory/file3.jar</a></pre>
           |</body>
           |</html>
    """.stripMargin.split("\n").toList

      assert(Source.fromInputStream(is).getLines().toList == htmlBody)
    }
    'test3 {
      val directoryUrl = new URL("s3c://localhost:4568/test.coursier-s3.com/directory/file2.jar")
      val is = s3cHandler.openConnection(directoryUrl).getInputStream

      assert(Source.fromInputStream(is).getLines().toList == List("File 2"))
    }
    'test4 {
      val directoryUrl = new URL("s3c://localhost:4568/test.coursier-s3.com/directory/file3.jar")
      val is = s3cHandler.openConnection(directoryUrl).getInputStream

      assert(Source.fromInputStream(is).getLines().toList == List("File 3"))
    }
  }

  private def write(file: File, content: String) = {
    val writer = new PrintWriter(file)
    writer.write(content)
    writer.close()
  }
}

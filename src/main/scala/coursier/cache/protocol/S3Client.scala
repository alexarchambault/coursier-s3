package coursier.cache.protocol

import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.nio.file.{Path, Paths}

import awscala.Credentials
import awscala.s3._
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.util.StringInputStream

import scala.collection.breakOut
import scala.io.{Codec, Source}
import scala.util.control.NonFatal
import scala.util.{Properties, Try}

object S3Client {

  def instance: S3 = {
    val cl = readFromEnv
      .orElse(readFromFile(Paths.get("").toAbsolutePath))
      .orElse(readFromFile(Paths.get(Properties.userHome)))
      .orElse(readFromFile(Paths.get(Properties.userHome).resolve(".sbt")))
      .orElse(readFromFile(Paths.get(Properties.userHome).resolve(".coursier")))
      .getOrElse(S3()(awscala.Region.EU_WEST_1))

    cl.setS3ClientOptions(S3ClientOptions.builder.setPathStyleAccess(true).build())

    cl
  }

  def setEndpoint(endpoint: String): Unit = {
    instance.setEndpoint(endpoint)
  }

  def prepareDirectoryList(bucket: Bucket, key: String): InputStream = {
    val files = instance.ls(bucket, key).map {
      case Left(directoryPrefix) => directoryPrefix
      case Right(s3ObjectSummary) => s3ObjectSummary.getKey
    }.map { key =>
      s"""<pre><a href=":$key">$key</a></pre>"""
    }.mkString("\n")

    val htmlBody =
      s"""
         |<html>
         |<head>
         |</head>
         |<body>
         |$files
         |</body>
         |</html>
    """.stripMargin

    new StringInputStream(htmlBody)
  }

  def prepareFileContents(bucket: Bucket, key: String): InputStream = {
    S3Object(bucket, instance.getObject(new GetObjectRequest(bucket.name, key))).content
  }

  private def readFromEnv: Option[S3] = {
    for {
      accessKey <- sys.env.get("AWS_ACCESS_KEY_ID")
      secretKey <- sys.env.get("AWS_SECRET_ACCESS_KEY")
    } yield {
      val region = sys.env.get("AWS_DEFAULT_REGION")
        .map(awscala.Region.apply)
        .getOrElse(awscala.Region.EU_WEST_1)

      S3(Credentials(accessKey, secretKey))(region)
    }
  }

  private def readFromFile(path: Path): Option[S3] = {
    val file = path.resolve(".s3credentials").toFile

    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    val sourceOpt = Try(Source.fromFile(file)).toOption

    try {
      sourceOpt.flatMap { f =>
        val cleanLines = f.getLines().toList
          .map(_.trim)
          .filter(l => l.nonEmpty && !l.startsWith("#"))

        val credentials: Map[String, String] =
          cleanLines.flatMap { l =>
            val values = l.split("=").map(_.trim)
            for {
              key <- values.lift(0)
              value <- values.lift(1)
            } yield key -> value
          }(breakOut)

        for {
          accessKey <- credentials.get("accessKey")
          secretKey <- credentials.get("secretKey")
        } yield {
          val region = credentials.get("region")
            .map(awscala.Region.apply)
            .getOrElse(awscala.Region.EU_WEST_1)
          S3(Credentials(accessKey, secretKey))(region)
        }
      }
    } catch {
      case NonFatal(e) =>
        None
    } finally {
      sourceOpt.foreach(_.close())
    }

  }

}

package coursier.cache.protocol

import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.nio.file.{Path, Paths}

import awscala.Credentials
import awscala.s3._
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.util.StringInputStream
import com.amazonaws.{ClientConfiguration, Protocol}

import scala.collection.breakOut
import scala.io.{Codec, Source}
import scala.util.{Properties, Try}

object S3Client {

  val instance: S3 = {
    val cl = readFromEnv
      .orElse(readFromFile(Paths.get("").toAbsolutePath))
      .orElse(readFromFile(Paths.get(Properties.userHome)))
      .orElse(readFromFile(Paths.get(Properties.userHome).resolve(".sbt")))
      .orElse(readFromFile(Paths.get(Properties.userHome).resolve(".coursier")))
      .getOrElse(S3()(
        sys.env.get("AWS_DEFAULT_REGION")
          .map(awscala.Region.apply)
          .getOrElse(awscala.Region.EU_WEST_1)
      ))

    cl.setS3ClientOptions(S3ClientOptions.builder.setPathStyleAccess(true).build())

    cl
  }

  def setEndpoint(endpoint: String): Unit = {
    instance.setEndpoint(endpoint)
  }

  def prepareDirectoryList(bucket: Bucket, key: String): InputStream = {
    val files = for {
      files <- instance.ls(bucket, key)
      fileKey <- files.fold({
        directoryPrefix => getLastSubPath(directoryPrefix).map(_ + "/")
      }, {
        s3ObjectSummary => getLastSubPath(s3ObjectSummary.getKey)
      })
    } yield
      s"""<pre><a href=":$fileKey">$fileKey</a></pre>"""

    val htmlBody =
      s"""<html>
          |<head>
          |</head>
          |<body>
          |${files.mkString("\n")}
          |</body>
          |</html>""".stripMargin

    new StringInputStream(htmlBody)
  }

  private def getLastSubPath(key: String): Option[String] = key
    .split("/")
    .map(_.trim)
    .filter(_.nonEmpty)
    .lastOption

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

      val protocol = sys.env.get("AWS_DEFAULT_PROTOCOL")
        .map(Protocol.valueOf)
        .getOrElse(Protocol.HTTPS)

      val config = new ClientConfiguration().withProtocol(protocol)

      S3(config, Credentials(accessKey, secretKey))(region)
    }
  }

  private def readFromFile(path: Path): Option[S3] = {
    val file = path.resolve(".s3credentials").toFile

    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    val sourceOpt = Try(Source.fromFile(file)).toOption

    val res = Try {
      sourceOpt.flatMap { f =>
        val cleanLines = f.getLines().toList
          .map(_.trim)
          .filter(l => l.nonEmpty && !l.startsWith("#"))

        val credentials: Map[String, String] =
          cleanLines.flatMap { l =>
            val values = l.split("=").map(_.trim)
            for {
              key <- values lift 0
              value <- values lift 1
            } yield key -> value
          }(breakOut)

        for {
          accessKey <- credentials.get("accessKey")
          secretKey <- credentials.get("secretKey")
        } yield {
          val region = credentials.get("region")
            .map(awscala.Region.apply)
            .getOrElse(awscala.Region.EU_WEST_1)

          val protocol = credentials.get("protocol")
            .map(Protocol.valueOf)
            .getOrElse(Protocol.HTTPS)

          val config = new ClientConfiguration().withProtocol(protocol)

          S3(config, Credentials(accessKey, secretKey))(region)
        }
      }
    }.toOption.flatten

    // Close the resources
    sourceOpt.foreach(_.close())

    res
  }

}

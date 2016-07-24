package coursier.cache.protocol

import java.io.InputStream
import java.net._

import awscala.s3._

import scala.util.{Failure, Success, Try}

/*
 * To avoid collision with `fm-sbt-s3-resolver` we added a different URL
 * format starting with `s3c` (S3 coursier).
 *
 * This way you can have your resolver URL with `s3c` and your publish URL with `s3`.
 *
 * Our handler only supports one kind of URL:
 * s3c://hostname[:port]/<bucket-name>
 *
 * For now the region in the url is being ignored.
 *
 * It does not support credentials in the URLs for security reasons.
 * You should provide them as environment variables or
 * in `.s3credentials` in $HOME, $HOME/.sbt, $HOME/.coursier
 */

class S3cHandler extends URLStreamHandler {

  override def openConnection(url: URL): URLConnection = {
    new URLConnection(url) {
      override def getInputStream: InputStream = {

        val endpoint = Option(url.getPort).filter(_ > 0).map(p => s"${url.getHost}:$p").getOrElse(url.getHost)
        S3Client.setEndpoint(endpoint)

        val fullPath = url.getPath
        val subPaths = fullPath.split("/").map(_.trim).filter(_.nonEmpty)

        subPaths.headOption.fold[InputStream] {
          Console.err.println(s"Failed to get bucket name in (url:${url.toString})")
          null
        } { bucketName =>
          // Bucket
          val bucket = Bucket(bucketName)

          // Key
          val key = subPaths.tail.mkString("/")

          Try {
            if (fullPath.endsWith("/")) {
              S3Client.prepareDirectoryList(bucket, s"$key/")
            } else {
              S3Client.prepareFileContents(bucket, key)
            }
          } match {
            case Success(f) => f
            case Failure(e) =>
              Console.err.println(s"Failed to prepare (key:$key): ${e.getMessage}")
              null
          }
        }

      }

      override def connect() {}

    }
  }

}

object S3cHandler {

  def setupS3Handler() = URL.setURLStreamHandlerFactory(S3URLStreamHandlerFactory)

  private object S3URLStreamHandlerFactory extends URLStreamHandlerFactory {
    def createURLStreamHandler(protocol: String): URLStreamHandler = protocol match {
      case "s3c" => new S3cHandler()
      case _ => null
    }
  }

}

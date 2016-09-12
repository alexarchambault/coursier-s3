package coursier.cache.protocol

import java.io.ByteArrayInputStream
import java.net._

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ListObjectsRequest

import scala.collection.JavaConverters._

/**
 * To avoid collision with `fm-sbt-s3-resolver` we added a different URL
 * format starting with `s3c` (S3 coursier).
 *
 * This way you can have your resolver URL with `s3c` and your publish URL with `s3`.
 *
 * Our handler only supports one kind of URL:
 * s3c://<bucket-name>/<key-path>
 */
class S3cHandler extends URLStreamHandlerFactory {

  def createURLStreamHandler(protocol: String): URLStreamHandler =
    if (protocol == "s3c")
      new URLStreamHandler {

        lazy val client = new AmazonS3Client

        for (endpoint <- sys.props.get("aws.s3Endpoint"))
          client.setEndpoint(endpoint)

        override def openConnection(url: URL) =
          new URLConnection(url) {

            override def getInputStream = {

              val bucket = url.getHost
              val path = url.getPath.stripPrefix("/")

              if (path.endsWith("/")) {

                val entries = client
                  .listObjects(
                    // "/" as a delimiter to be returned only entries in the first level (no recursion),
                    // with (pseudo) sub-directories indeed ending with a "/"
                    new ListObjectsRequest(bucket, path, null, "/", null)
                  )
                  .getObjectSummaries
                  .asScala
                  .map { summary =>
                    val key = summary.getKey
                    assert(key.startsWith(path))

                    val name = key.stripPrefix(path)

                    // TODO escape characters?
                    s"""<li><a href="$name">$name</a></li>"""
                  }

                if (entries.isEmpty)
                  throw new NoSuchElementException(s"$url")
                else {
                  val page =
                    s"""<!DOCTYPE html>
                       |<html>
                       |<head>
                       |</head>
                       |<body>
                       |<ul>
                       |${entries.mkString}
                       |</ul>
                       |</body>
                       |</html>
                     """.stripMargin

                  val b = page.getBytes("UTF-8")
                  new ByteArrayInputStream(b)
                }
              } else
                client
                  .getObject(bucket, path)
                  .getObjectContent
            }

            override def connect() = {
              // initialize client if not already initialized
              client
            }
          }
      }
    else
      throw new IllegalArgumentException(s"protocol $protocol")

}
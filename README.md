## S3 Coursier Plugin

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/600076d152dc40429e5e07003114b2d0)](https://www.codacy.com/app/Codacy/coursier-s3?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=rtfpessoa/coursier-s3&amp;utm_campaign=Badge_Grade)
[![CircleCI](https://circleci.com/gh/rtfpessoa/coursier-s3/tree/master.svg?style=svg)](https://circleci.com/gh/rtfpessoa/coursier-s3/tree/master)

[coursier](https://github.com/alexarchambault/coursier) plugin adding support for s3 dependency resolution.

For compatibility purposes with [fm-sbt-s3-resolver](https://github.com/frugalmechanic/fm-sbt-s3-resolver),
urls use `s3c://` instead of `s3://`.

### Credentials

* Environment

```sh
AWS_ACCESS_KEY_ID="myKey"
AWS_SECRET_ACCESS_KEY="myVeryS3cret"
AWS_DEFAULT_REGION="EU_WEST_1"
```

* File

> File named `.s3credentials` can be placed in one of the following locations: `current directory`, `$HOME`, `$HOME/.sbt`, `$HOME/.coursier`

```ini
# Credentials
accessKey = myKey
secretKey = myVeryS3cret

# Region
region = EU_WEST_1
```

### Usage

1. Add the plugin as a library dependency in `project/plugins.sbt`

    ```sbt
    resolvers += Resolver.bintrayRepo("rtfpessoa", "maven")

    libraryDependencies += "rtfpessoa" %% "coursier-s3" % "1.0.0-alpha.1"
    ```

2. Setup support for `s3c` urls

    > This step is required to add support for `s3c` URLs in the JVM

    * **Option 1** - create object in `project/Common.scala`

    ```scala
    import coursier.cache.protocol.S3cHandler

    object Common {
        S3cHandler.setupS3Handler()
    }
    ```

    * **Option 2** -  add it to `build.sbt`

    ```scala
    import coursier.cache.protocol.S3cHandler
    S3cHandler.setupS3Handler()
    ```

3. Add s3 resolvers, without or with ivy patterns (use `s3c` to prefix the url)

    ```sbt
    resolvers += "S3 resolver" at "s3c://s3-eu-west-1.amazonaws.com/private.mvn.example.com"
    ```

    ```sbt
    resolvers += Resolver.url("S3 resolver", url("s3c://s3-eu-west-1.amazonaws.com/private.mvn.example.com"))(Resolver.defaultIvyPatterns)
    ```

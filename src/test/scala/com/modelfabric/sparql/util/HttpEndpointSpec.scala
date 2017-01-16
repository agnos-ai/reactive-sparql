package com.modelfabric.sparql.util

import org.scalatest.{WordSpec, MustMatchers}

class HttpEndpointSpec extends WordSpec with MustMatchers  {

  "The HttpEndpointSuiteTestRunner object" must {

    "1. infer the default values with port automation" in {

      val endpoint = HttpEndpoint.localhostWithAutomaticPort("/test")

      assert(endpoint.protocol === "http")
      assert(endpoint.host === "localhost")
      assert(endpoint.port > 100)
      assert(endpoint.path === "/test")
      assert(endpoint.url.startsWith("http://localhost:"))
      assert(endpoint.url.endsWith("/test"))
    }

    "2. parse a trivial URL" in {

      val endpoint = HttpEndpoint("my-host.my-domain.com")

      assert(endpoint.protocol === "http")
      assert(endpoint.host === "my-host.my-domain.com")
      assert(endpoint.port === 80)
      assert(endpoint.url === "http://my-host.my-domain.com")
    }

    "3. parse a trivial URL with HTTPS" in {

      val endpoint = HttpEndpoint("https://my-host.my-domain.com")

      assert(endpoint.protocol === "https")
      assert(endpoint.host === "my-host.my-domain.com")
      assert(endpoint.port === 443)
      assert(endpoint.url === "https://my-host.my-domain.com")
    }

    "4. parse a URL with a port" in {

      val endpoint = HttpEndpoint("https://my-host.my-domain.com:1234")

      assert(endpoint.protocol === "https")
      assert(endpoint.host === "my-host.my-domain.com")
      assert(endpoint.port === 1234)
      assert(endpoint.path === "")
      assert(endpoint.url === "https://my-host.my-domain.com:1234")
    }

    "5. parse a URL with a resource" in {

      val endpoint = HttpEndpoint("https://my-host.my-domain.com/resources/qwerty")

      assert(endpoint.protocol === "https")
      assert(endpoint.host === "my-host.my-domain.com")
      assert(endpoint.port === 443)
      assert(endpoint.url === "https://my-host.my-domain.com/resources/qwerty")
    }

    "6. parse a URL with a port and resource" in {

      val endpoint = HttpEndpoint("https://my-host.my-domain.com:12345/resources/qwerty")

      assert(endpoint.protocol === "https")
      assert(endpoint.host === "my-host.my-domain.com")
      assert(endpoint.port === 12345)
      assert(endpoint.url === "https://my-host.my-domain.com:12345/resources/qwerty")
    }

  }

  "The HttpEndpoint unapply() extractor" must {

    "7. make sure unapply() extractor works correctly" in {

      val endpoint = HttpEndpoint("https://my-host.my-domain.com:12345/resources/qwerty")

      endpoint match {
        case HttpEndpoint(protocol, host, int, resource, auth) =>
          assert(endpoint.protocol === "https")
          assert(endpoint.host === "my-host.my-domain.com")
          assert(endpoint.port === 12345)
          assert(endpoint.url === "https://my-host.my-domain.com:12345/resources/qwerty")
          assert(endpoint.authentication === None)
        case _ =>
          assert(false, "unapply() failed")
      }
    }

    "8. make sure unapply() extractor works correctly even with the Authentication" in {

      val endpoint = HttpEndpoint("https://my-host.my-domain.com:12345/resources/qwerty", authentication = Some(BasicAuthentication("admin", "admin")))

      endpoint match {
        case HttpEndpoint(protocol, host, int, resource, auth) =>
          assert(endpoint.protocol === "https")
          assert(endpoint.host === "my-host.my-domain.com")
          assert(endpoint.port === 12345)
          assert(endpoint.url === "https://my-host.my-domain.com:12345/resources/qwerty")
          assert(endpoint.authentication === Some(BasicAuthentication("admin", "admin")))
        case _ =>
          assert(false, "unapply() failed")
      }
    }
  }

}

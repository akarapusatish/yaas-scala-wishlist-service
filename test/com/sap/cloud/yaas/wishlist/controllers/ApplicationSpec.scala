/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2016 hybris AG
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of hybris
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with hybris.
 */
package com.sap.cloud.yaas.wishlist.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.sap.cloud.yaas.servicesdk.patternsupport.common.ErrorResponses
import com.sap.cloud.yaas.servicesdk.patternsupport.traits.{CountableTrait, PagedTrait, YaasAwareTrait}
import com.sap.cloud.yaas.wishlist.controllers.ApplicationSpec._
import com.sap.cloud.yaas.wishlist.model.Wishlist.Wishlists
import com.sap.cloud.yaas.wishlist.model.{OAuthToken, ResourceLocation, Wishlist, WishlistItem}
import com.sap.cloud.yaas.wishlist.security.Scopes
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside._
import org.scalatestplus.play._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import play.api.test._

class ApplicationSpec extends PlaySpec with OneAppPerSuite with BeforeAndAfterAll {

  val wishlistItem = new WishlistItem("product", TEST_AMOUNT, Some("note"), None)
  val wishlistInvalidItem = new WishlistItem("", 0, Some("note"), None)
  val wishlist = new Wishlist(TEST_ID, "owner", "title", List(wishlistItem))
  val invalidWishlist = new Wishlist(TEST_ID, "owner", "title", List(wishlistItem, wishlistInvalidItem))

  val wireMockServer: WireMockServer = new WireMockServer(
    WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))

  override def beforeAll(): Unit = {
    val wiremockUrl = s"http://localhost:$WIREMOCK_PORT"
    sys.props ++= Map(YAAS_DOCUMENT_URL -> wiremockUrl,
      YAAS_SECURITY_OAUTH_URL -> wiremockUrl,
      YAAS_CLIENT -> TEST_CLIENT,
      BASIC_AUTH_CREDENTIALS -> "wishlist:test"
    )
    wireMockServer.start()
    configureFor("localhost", WIREMOCK_PORT)
    def stubOauthService(): Unit = {
      stubFor(post(urlEqualTo("/token"))
        .willReturn(
          aResponse().withStatus(OK).withBody(Json.toJson(
            new OAuthToken("", TEST_TOKEN, TEST_TOKEN_EXPIRY, "")).toString)
        ))
    }
    stubOauthService()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    sys.props -= YAAS_DOCUMENT_URL
    sys.props -= YAAS_SECURITY_OAUTH_URL
    sys.props -= YAAS_CLIENT
    wireMockServer.stop()
    super.afterAll()
  }

  "Application" must {

    "create a wishlist in document service propagating the hybris-request-id" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"

      stubFor(post(urlEqualTo(path))
        .withHeader(HeaderNames.CONTENT_TYPE, containing(JSON))
        .withHeader(YaasAwareTrait.Headers.REQUEST_ID, equalTo(TEST_REQUEST_ID))
        .withHeader(YaasAwareTrait.Headers.HOP, equalTo(TEST_HOP))
        .withHeader(HeaderNames.AUTHORIZATION, containing(TEST_TOKEN))
        .willReturn(
          aResponse().withStatus(CREATED)
            .withHeader(HeaderNames.CONTENT_TYPE, JSON)
            .withBody(Json.toJson(new ResourceLocation(TEST_ID, TEST_LINK)).toString())
        )
      )
      val wishlistJson = Json.toJson(wishlist)
      val request = FakeRequest(POST, WISHLIST_PATH)
        .withHeaders(defaultHeaders: _*)
        .withHeaders(
          YaasAwareTrait.Headers.REQUEST_ID -> TEST_REQUEST_ID,
          YaasAwareTrait.Headers.HOP -> TEST_HOP
        )
        .withBody(wishlistJson)

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe OK
          contentType(result) mustEqual Some(JSON)
          (contentAsJson(result) \ "id").get mustEqual JsString(TEST_ID)
          (contentAsJson(result) \ "link").get mustEqual JsString(TEST_LINK)
      }
      WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(path)).withRequestBody(
        WireMock.equalToJson(wishlistJson.toString())))
    }

    "update a wishlist in document service propagating the hybris-request-id" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"

      stubFor(put(urlEqualTo(path))
        .withHeader(HeaderNames.CONTENT_TYPE, containing(JSON))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody( """{"code" : "200","status" : "200","message" : "Operation succeeded"}"""))
      )
      val wishlistJson = Json.toJson(wishlist)
      val request = FakeRequest(PUT, WISHLIST_PATH + s"/$TEST_ID")
        .withHeaders(defaultHeaders: _*)
        .withHeaders(
          YaasAwareTrait.Headers.REQUEST_ID -> TEST_REQUEST_ID,
          YaasAwareTrait.Headers.HOP -> TEST_HOP
        )
        .withBody(wishlistJson)

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe OK
      }
      WireMock.verify(WireMock.putRequestedFor(WireMock.urlMatching(path)).withRequestBody(
        WireMock.equalToJson(wishlistJson.toString()))
        .withHeader(YaasAwareTrait.Headers.CLIENT, equalTo(TEST_CLIENT))
        .withHeader(YaasAwareTrait.Headers.TENANT, equalTo(TEST_TENANT))
        .withHeader(YaasAwareTrait.Headers.REQUEST_ID, equalTo(TEST_REQUEST_ID))
        .withHeader(YaasAwareTrait.Headers.HOP, equalTo(TEST_HOP))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $TEST_TOKEN"))
      )
    }

    "delete a wishlist in document service propagating the hybris-request-id" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"

      stubFor(delete(urlEqualTo(path))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT))
      )
      val wishlistJson = Json.toJson(wishlist)
      val request = FakeRequest(DELETE, WISHLIST_PATH + s"/$TEST_ID")
        .withHeaders(defaultHeaders: _*)
        .withHeaders(
          YaasAwareTrait.Headers.REQUEST_ID -> TEST_REQUEST_ID,
          YaasAwareTrait.Headers.HOP -> TEST_HOP
        )

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe NO_CONTENT
      }
      WireMock.verify(WireMock.deleteRequestedFor(WireMock.urlMatching(path))
        .withHeader(YaasAwareTrait.Headers.CLIENT, equalTo(TEST_CLIENT))
        .withHeader(YaasAwareTrait.Headers.TENANT, equalTo(TEST_TENANT))
        .withHeader(YaasAwareTrait.Headers.REQUEST_ID, equalTo(TEST_REQUEST_ID))
        .withHeader(YaasAwareTrait.Headers.HOP, equalTo(TEST_HOP))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $TEST_TOKEN"))
      )
    }

    "return one wishlist by id forwarding the hybris-request-id" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"

      stubFor(get(urlPathEqualTo(path))
        .withHeader(HeaderNames.AUTHORIZATION, containing(TEST_TOKEN))
        .willReturn(
          aResponse().withStatus(OK)
            .withHeader(HeaderNames.CONTENT_TYPE, JSON)
            .withBody(
              s"""{
                  |    "owner": "owner1",
                  |    "title": "title1",
                  |    "items": [
                  |        {
                  |            "product": "hybris mug",
                  |            "amount": 50
                  |        },
                  |        {
                  |            "product": "hybris hoodie",
                  |            "amount": 25
                  |        }
                  |    ],
                  |    "id": "$TEST_ID"
                  |}""".stripMargin('|'))
        )
      )
      val request = FakeRequest(GET, WISHLIST_PATH + s"/$TEST_ID")
        .withHeaders(defaultHeaders: _*)
        .withHeaders(
          YaasAwareTrait.Headers.REQUEST_ID -> TEST_REQUEST_ID,
          YaasAwareTrait.Headers.HOP -> TEST_HOP
        )

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe OK
          contentType(result) mustEqual Some(JSON)
          val wishlist: Wishlist = Json.fromJson[Wishlist](contentAsJson(result)).get
          wishlist.id mustBe TEST_ID
          wishlist.title mustBe "title1"
          wishlist.owner mustBe "owner1"
          wishlist.items.head.product mustBe "hybris mug"
          wishlist.items.head.amount mustBe 50
          wishlist.items(1).product mustBe "hybris hoodie"
          wishlist.items(1).amount mustBe 25
      }
      WireMock.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(path))
        .withHeader(YaasAwareTrait.Headers.CLIENT, equalTo(TEST_CLIENT))
        .withHeader(YaasAwareTrait.Headers.TENANT, equalTo(TEST_TENANT))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $TEST_TOKEN"))
        .withHeader(YaasAwareTrait.Headers.REQUEST_ID, equalTo(TEST_REQUEST_ID))
        .withHeader(YaasAwareTrait.Headers.HOP, equalTo(TEST_HOP)))
    }

    "return all wishlists with paging" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist"

      stubFor(get(urlPathEqualTo(path))
        .withHeader(HeaderNames.AUTHORIZATION, containing(TEST_TOKEN))
        .willReturn(
          aResponse().withStatus(OK)
            .withHeader(HeaderNames.CONTENT_TYPE, JSON)
            .withBody(
              """[
                |    {
                |        "owner": "owner1",
                |        "title": "title1",
                |        "items": [
                |            {
                |                "product": "hybris mug",
                |                "amount": 50
                |            },
                |            {
                |                "product": "hybris hoodie",
                |                "amount": 25
                |            }
                |        ],
                |        "id": "id1"
                |    },
                |    {
                |        "owner": "owner2",
                |        "title": "title2",
                |        "items": [
                |            {
                |                "product": "hybris mug",
                |                "amount": 10
                |            }
                |        ],
                |        "id": "id2"
                |    }
                |]""".stripMargin('|'))
        )
      )
      val request = FakeRequest(GET, WISHLIST_PATH + s"?pageSize=2&pageNumber=3&totalCount=true")
        .withHeaders(defaultHeaders: _*)
        .withHeaders(
          YaasAwareTrait.Headers.REQUEST_ID -> TEST_REQUEST_ID,
          YaasAwareTrait.Headers.HOP -> TEST_HOP
        )

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe OK
          contentType(result) mustEqual Some(JSON)
          val wishlists: Wishlists = Json.fromJson[Wishlists](contentAsJson(result)).get
          wishlists.size mustBe 2
          wishlists.head.id mustBe "id1"
          wishlists.head.title mustBe "title1"
          wishlists.head.owner mustBe "owner1"
          wishlists.head.items.head.product mustBe "hybris mug"
          wishlists.head.items.head.amount mustBe 50
          wishlists.head.items(1).product mustBe "hybris hoodie"
          wishlists.head.items(1).amount mustBe 25
          wishlists(1).id mustBe "id2"
          wishlists(1).title mustBe "title2"
          wishlists(1).owner mustBe "owner2"
          wishlists(1).items.head.product mustBe "hybris mug"
          wishlists(1).items.head.amount mustBe 10
      }
      WireMock.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(path))
        .withHeader(YaasAwareTrait.Headers.CLIENT, equalTo(TEST_CLIENT))
        .withHeader(YaasAwareTrait.Headers.TENANT, equalTo(TEST_TENANT))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo(s"Bearer $TEST_TOKEN"))
        .withHeader(YaasAwareTrait.Headers.REQUEST_ID, equalTo(TEST_REQUEST_ID))
        .withHeader(YaasAwareTrait.Headers.HOP, equalTo(TEST_HOP))
        .withQueryParam(PagedTrait.QueryParameters.PAGE_SIZE, equalTo("2"))
        .withQueryParam(PagedTrait.QueryParameters.PAGE_NUMBER, equalTo("3"))
        .withQueryParam(CountableTrait.QueryParameters.TOTAL_COUNT, equalTo("true")))
    }

    "return a conflict for an already existing wishlist" in {
      stubFor(post(urlEqualTo(s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing(JSON))
        .willReturn(
          aResponse().withStatus(CONFLICT)
        )
      )
      val request = FakeRequest(POST, WISHLIST_PATH)
        .withHeaders(defaultHeaders: _*)
        .withBody(Json.toJson(wishlist))

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe CONFLICT
      }
    }

    "return a 400 for an invalid wishlist" in {
      val request = FakeRequest(POST, WISHLIST_PATH)
        .withHeaders(defaultHeaders: _*)
        .withBody(Json.toJson(invalidWishlist))

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe BAD_REQUEST
      }
    }

    "return a 400 for an invalid wishlist json" in {
      val request = FakeRequest(POST, WISHLIST_PATH)
        .withHeaders(defaultHeaders: _*)
        .withBody("{ \"invalid\" }")

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe BAD_REQUEST
      }
    }

    "return a 500 for unexpected errors from the document repository" in {
      stubFor(post(urlEqualTo(s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing(JSON))
        .willReturn(
          aResponse().withStatus(INTERNAL_SERVER_ERROR)
        )
      )
      val request = FakeRequest(POST, WISHLIST_PATH)
        .withHeaders(defaultHeaders: _*)
        .withBody(Json.toJson(wishlist))

      inside(route(app, request)) {
        case Some(result) =>
          status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

    "call document service multiple times, receiving 500s to open circuit" in {
      stubFor(get(urlPathMatching(s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist"))
        .willReturn(
          aResponse().withStatus(SERVICE_UNAVAILABLE)
        )
      )
      val request = FakeRequest(GET, WISHLIST_PATH)
        .withHeaders(defaultHeaders: _*)

      val max_failures = app.configuration.getInt("yaas.document.max_failures").get
      for (a <- 1 to (max_failures + 2)) {
        inside(route(app, request)) {
          case Some(result) =>
            status(result) mustBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "missing required Yaas headers should result in a well formatted Bad Request" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"
      val wishlistJson = Json.toJson(wishlist)
      val request = FakeRequest(PUT, WISHLIST_PATH + s"/$TEST_ID")
        .withHeaders(
          YaasAwareTrait.Headers.REQUEST_ID -> TEST_REQUEST_ID,
          YaasAwareTrait.Headers.HOP -> TEST_HOP
        )
        .withBody(wishlistJson)
      inside(route(app, request)) {
        case Some(result) =>
          val contentJson = contentAsJson(result)
          status(result) mustBe BAD_REQUEST
          contentType(result) mustEqual Some(JSON)
          (contentJson \ "type").get mustEqual JsString("missing_required_header")
          (contentJson \ "message").get mustEqual JsString(
            "Header 'hybris-tenant' is required but was not provided in the request.")
          (contentJson \ "moreInfo").get mustEqual JsString(
            ErrorResponses.DOCUMENTATION_LINK.toString)
      }
    }

    "invalid basic authorization header results in a 401 Unauthorized error" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"
      val wishlistJson = Json.toJson(wishlist)
      val request = FakeRequest(PUT, WISHLIST_PATH + s"/$TEST_ID")
        .withHeaders(defaultHeaders: _*)
        .withHeaders(HeaderNames.AUTHORIZATION -> "Basic invalid")
        .withBody(wishlistJson)
      inside(route(app, request)) {
        case Some(result) =>
          val contentJson = contentAsJson(result)
          status(result) mustBe UNAUTHORIZED
          contentType(result) mustEqual Some(JSON)
          (contentJson \ "type").get mustEqual JsString("insufficient_credentials")
      }
    }

    "invalid scope header results in a 403 Forbidden error" in {
      val path = s"/$TEST_TENANT/$TEST_CLIENT/data/wishlist/$TEST_ID"
      val wishlistJson = Json.toJson(wishlist)
      val request = FakeRequest(PUT, WISHLIST_PATH + s"/$TEST_ID")
        .withHeaders(defaultHeaders: _*)
        .withHeaders(YaasAwareTrait.Headers.SCOPES -> Scopes.VIEW_SCOPE)
        .withBody(wishlistJson)
      inside(route(app, request)) {
        case Some(result) =>
          val contentJson = contentAsJson(result)
          status(result) mustBe FORBIDDEN
          contentType(result) mustEqual Some(JSON)
          (contentJson \ "type").get mustEqual JsString("insufficient_permissions")
      }
    }
  }

  val defaultHeaders: Seq[(String, String)] = Seq(
    HeaderNames.CONTENT_TYPE -> MimeTypes.JSON,
    YaasAwareTrait.Headers.TENANT -> TEST_TENANT,
    YaasAwareTrait.Headers.CLIENT -> TEST_CLIENT,
    YaasAwareTrait.Headers.SCOPES -> Scopes.MANAGE_SCOPE,
    HeaderNames.AUTHORIZATION -> AUTHORIZATION_BASIC)

}

object ApplicationSpec {

  val YAAS_DOCUMENT_URL = "yaas.document.url"
  val YAAS_SECURITY_OAUTH_URL = "yaas.security.oauth_url"
  val YAAS_CLIENT = "yaas.client"
  val WISHLIST_PATH = "/wishlists"
  val BASIC_AUTH_CREDENTIALS = "BASIC_AUTH_CREDENTIALS"
  val AUTHORIZATION_BASIC = "Basic d2lzaGxpc3Q6dGVzdA=="
  val BASE_URI = "https://api.yaas.io/hybris/wishlist/v1"

  val WIREMOCK_PORT = 8089

  val TEST_TENANT = "mytenant"
  val TEST_CLIENT = "mytenant.myclient"
  val TEST_ID = "415-2"
  val TEST_LINK = "http://myLink.com"
  val TEST_REQUEST_ID = "516-3"
  val TEST_HOP = "4"
  val TEST_TOKEN = "token"
  val TEST_TOKEN_EXPIRY = 3600
  val TEST_AMOUNT = 4
}

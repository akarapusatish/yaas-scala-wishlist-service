package com.sap.cloud.yaas.wishlist.security

import com.sap.cloud.yaas.wishlist.context.YaasAwareParameters
import play.api.mvc.{Request, WrappedRequest}

/**
  * Wraps a regular request into a yaas aware parameter enriched YaasRequest
  */
case class YaasRequest[A](yaasContext: YaasAwareParameters, request: Request[A])
  extends WrappedRequest[A](request)

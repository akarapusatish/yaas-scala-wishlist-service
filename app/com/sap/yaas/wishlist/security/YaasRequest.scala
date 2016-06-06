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
package com.sap.yaas.wishlist.security

import com.sap.yaas.wishlist.model.YaasAwareParameters
import play.api.mvc.{ Request, WrappedRequest }

/**
 * Wraps a regular request into a yaas aware parameter enriched YaasRequest
 */
case class YaasRequest[A](val yaasContext: YaasAwareParameters, request: Request[A])
  extends WrappedRequest[A](request)

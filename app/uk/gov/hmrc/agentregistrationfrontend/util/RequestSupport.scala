/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentregistrationfrontend.util

import play.api.i18n.*
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentregistration.shared.audit.SessionId
import uk.gov.hmrc.agentregistrationfrontend.util.Errors.getOrThrowExpectedDataMissing
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.binders.AbsoluteWithHostnameFromAllowlist
import uk.gov.hmrc.play.bootstrap.binders.OnlyRelative
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider

import javax.inject.Inject

/** I'm repeating a pattern which was brought originally by play-framework and putting some more data which can be derived from a request
  *
  * Use it to provide HeaderCarrier, Lang, or Messages
  */
class RequestSupport @Inject() (i18nSupport: I18nSupport):

  def lang(using requestHeader: RequestHeader): Lang = i18nSupport.request2Messages(using requestHeader).lang

  given legacyMessages(using requestHeader: RequestHeader): Messages = i18nSupport.request2Messages(using requestHeader)

  given hc(using request: RequestHeader): HeaderCarrier = RequestSupport.hc

object RequestSupport:

  given hc(using request: RequestHeader): HeaderCarrier = HcProvider.headerCarrier

  /** Naive way of checking if user is logged in. Use it in views only. For more real check see auth.AuthService
    */
  def isSignedIn(using request: RequestHeader): Boolean = request.session.get(SessionKeys.authToken).isDefined

  def validateRedirectUrl(
    redirectUrl: RedirectUrl,
    allowedHosts: Set[String]
  ): String =
    if RedirectUrl.isRelativeUrl(redirectUrl.unsafeValue) then {
      redirectUrl.get(OnlyRelative).url
    }
    else {
      redirectUrl.get(AbsoluteWithHostnameFromAllowlist(allowedHosts)).url
    }

  def getCurrentSessionId(using request: RequestHeader): SessionId = SessionId(hc.sessionId.getOrThrowExpectedDataMissing("sessionId").value)

  /** This is because we want to give responsibility of creation of HeaderCarrier to the platform code. If they refactor how hc is created our code will pick it
    * up automatically.
    */
  private object HcProvider
  extends FrontendHeaderCarrierProvider:
    def headerCarrier(using request: RequestHeader): HeaderCarrier = this.hc(using request)

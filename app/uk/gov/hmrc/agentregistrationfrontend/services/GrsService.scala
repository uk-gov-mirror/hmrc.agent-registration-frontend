/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentregistrationfrontend.services

import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentregistration.shared.BusinessType
import uk.gov.hmrc.agentregistrationfrontend.config.AppLangs
import uk.gov.hmrc.agentregistrationfrontend.config.GrsConfig
import uk.gov.hmrc.agentregistrationfrontend.connectors.GrsConnector
import uk.gov.hmrc.agentregistrationfrontend.controllers.AppRoutes
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyConfig
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyData
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyId
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyLabels
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyStartUrl
import uk.gov.hmrc.agentregistrationfrontend.model.grs.TranslationLabels

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class GrsService @Inject() (
  grsConnector: GrsConnector,
  messagesApi: MessagesApi,
  grsConfig: GrsConfig
):

  def createGrsJourney(
    businessType: BusinessType,
    includeNamePageLabel: Boolean
  )(using
    request: RequestHeader
  ): Future[JourneyStartUrl] = grsConnector.createJourney(
    journeyConfig = createJourneyConfig(includeNamePageLabel),
    businessType = businessType
  )

  def getJourneyData(
    businessType: BusinessType,
    journeyId: JourneyId
  )(using request: RequestHeader): Future[JourneyData] = grsConnector.getJourneyData(businessType, journeyId)

  private def createJourneyConfig(
    includeNamePageLabel: Boolean
  ): JourneyConfig = {

    val fullNamePageLabel: Option[String] = if includeNamePageLabel then messagesApi.translate("grs.optFullNamePageLabel", Nil)(using Lang("en")) else None
    val welshFullNamePageLabel: Option[String] = if includeNamePageLabel then messagesApi.translate("grs.optFullNamePageLabel", Nil)(using Lang("cy")) else None

    JourneyConfig(
      continueUrl = AppRoutes.apply.internal.GrsController.journeyCallback(None).url,
      deskProServiceId = grsConfig.deskProServiceId,
      signOutUrl = AppRoutes.SignOutController.signOut.url,
      accessibilityUrl = grsConfig.accessibilityUrl,
      regime = grsConfig.regime,
      businessVerificationCheck = false,
      labels = Some(JourneyLabels(
        en = TranslationLabels(
          optServiceName = messagesApi.translate("service.name", Nil)(using AppLangs.en),
          optFullNamePageLabel = fullNamePageLabel
        ),
        cy = TranslationLabels(
          optServiceName = messagesApi.translate("service.name", Nil)(using AppLangs.cy),
          optFullNamePageLabel = welshFullNamePageLabel
        )
      ))
    )
  }

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

package uk.gov.hmrc.agentregistrationfrontend.controllers.individual.riskingoutcome.fixablefailures

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.BusinessPartnerRecordResponse
import uk.gov.hmrc.agentregistration.shared.LinkId
import uk.gov.hmrc.agentregistration.shared.risking.IndividualFix._10.IndividualDetailsFix
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeApplication
import uk.gov.hmrc.agentregistrationfrontend.action.individual.IndividualActions
import uk.gov.hmrc.agentregistrationfrontend.controllers.individual.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.util.DisplayDate.displayDateForLang
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.riskingoutcome.fixablefailures.FixIndividualProvidedDetailsPage

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FixIndividualProvidedDetailsController @Inject() (
  mcc: MessagesControllerComponents,
  actions: IndividualActions,
  view: FixIndividualProvidedDetailsPage
)
extends FrontendController(mcc, actions):

  def show(
    linkId: LinkId
  ): Action[AnyContent] =
    authorisedWithFixableDetails(linkId):
      implicit request =>
        val correctiveActionExpiryDate: LocalDate =
          request.get[RiskingOutcomeApplication] match
            case o: RiskingOutcomeApplication.FailedFixable => o.correctiveActionExpiryDate
            case o: RiskingOutcomeApplication.FailedNonFixable => o.correctiveActionExpiryDate
            case o: RiskingOutcomeApplication.Approved =>
              throw new IllegalStateException("Individual provided details should not be shown for approved applications.")

        Ok(view(
          failureCode = request.get[IndividualDetailsFix].toString,
          correctiveActionExpiryDate = displayDateForLang(correctiveActionExpiryDate),
          linkId = linkId
        ))

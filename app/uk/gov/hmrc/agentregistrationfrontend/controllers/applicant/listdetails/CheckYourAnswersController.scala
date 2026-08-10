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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.listdetails

import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.AgentApplication.IsNotSoleTrader
import uk.gov.hmrc.agentregistration.shared.AgentApplicationSoleTrader
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.lists.FiveOrLess
import uk.gov.hmrc.agentregistration.shared.lists.FiveOrLessOfficers
import uk.gov.hmrc.agentregistration.shared.lists.NumberOfIndividuals
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.services.individual.IndividualProvideDetailsService
import uk.gov.hmrc.agentregistrationfrontend.views.html.applicant.listdetails.CheckYourAnswersPage

@Singleton
class CheckYourAnswersController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  view: CheckYourAnswersPage,
  individualProvideDetailsService: IndividualProvideDetailsService
)
extends FrontendController(mcc, actions):

  private type DataWithLists = List[IndividualProvidedDetails] *: IsNotSoleTrader *: DataWithAuth

  private val baseAction: ActionBuilderWithData[DataWithLists] = actions
    .getApplicationInProgress
    .refine:
      implicit request =>
        request.get[AgentApplication] match
          case _: AgentApplicationSoleTrader =>
            logger.warn("Sole traders do not add other relevant individuals to a list, redirecting to task list for the correct links")
            Redirect(AppRoutes.apply.TaskListController.show.url)
          case aa: IsNotSoleTrader => request.replace[AgentApplication, IsNotSoleTrader](aa)
    .refine:
      implicit request =>
        val agentApplication: IsNotSoleTrader = request.get[IsNotSoleTrader]
        individualProvideDetailsService
          .findAllByApplicationId(agentApplication.agentApplicationId)
          .map: individualsList =>
            request.add[List[IndividualProvidedDetails]](individualsList)
    .ensure(
      condition =
        implicit request =>
          val numberOfKeyIndividuals: Int = request.get[List[IndividualProvidedDetails]].count(_.isPersonOfControl)
          request.get[IsNotSoleTrader] match
            case a: AgentApplication.IsAgentApplicationForDeclaringNumberOfKeyIndividuals =>
              NumberOfIndividuals.isKeyIndividualListComplete(numberOfKeyIndividuals, a.numberOfIndividuals)
            case a: AgentApplication.IsIncorporated => NumberOfIndividuals.isKeyIndividualListComplete(numberOfKeyIndividuals, a.numberOfIndividuals),
      resultWhenConditionNotMet =
        implicit request =>
          request.get[IsNotSoleTrader] match
            case _: AgentApplication.IsAgentApplicationForDeclaringNumberOfKeyIndividuals =>
              Redirect(AppRoutes.apply.listdetails.nonincorporated.CheckYourAnswersController.show)
            case _: AgentApplication.IsIncorporated => Redirect(AppRoutes.apply.listdetails.incoporated.CheckYourAnswersController.show)
    )
    .ensure(
      condition =
        implicit request =>
          val numberOfOtherRelevantIndividuals: Int = request.get[List[IndividualProvidedDetails]].count(!_.isPersonOfControl)
          request.get[IsNotSoleTrader].hasOtherRelevantIndividuals match
            case Some(true) if (numberOfOtherRelevantIndividuals > 0) => true
            case Some(false) => true
            case _ => false,
      resultWhenConditionNotMet =
        implicit request =>
          request.get[IsNotSoleTrader].numberOfIndividuals match
            case Some(FiveOrLess(n)) if n === 0 =>
              Redirect(AppRoutes.apply.listdetails.otherrelevantindividuals.MandatoryRelevantIndividualsController.show.url)
            case Some(FiveOrLessOfficers(n, true)) if n === 0 =>
              Redirect(AppRoutes.apply.listdetails.otherrelevantindividuals.MandatoryRelevantIndividualsController.show.url)
            case _ => Redirect(AppRoutes.apply.listdetails.otherrelevantindividuals.ConfirmOtherRelevantIndividualsController.show.url)
    )

  def show: Action[AnyContent] = baseAction:
    implicit request =>
      val allIndividualsList: List[IndividualProvidedDetails] = request.get
      val listOfKeyIndividuals: List[IndividualProvidedDetails] = allIndividualsList.filter(_.isPersonOfControl)
      val listOfOtherRelevantIndividuals: List[IndividualProvidedDetails] = allIndividualsList.filterNot(_.isPersonOfControl)
      val agentApplication = request.get[IsNotSoleTrader]

      Ok(view(
        agentApplication = agentApplication,
        listOfKeyIndividuals = listOfKeyIndividuals,
        listOfOtherRelevantIndividuals = listOfOtherRelevantIndividuals
      ))

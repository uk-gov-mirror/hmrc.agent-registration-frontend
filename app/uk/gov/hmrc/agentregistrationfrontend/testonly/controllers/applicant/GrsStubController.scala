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

package uk.gov.hmrc.agentregistrationfrontend.testonly.controllers.applicant

import play.api.data.FieldMapping
import play.api.data.Form
import play.api.data.Forms
import play.api.data.Forms.mapping
import play.api.data.Forms.nonEmptyText
import play.api.data.Forms.optional
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import play.api.mvc.Request
import uk.gov.hmrc.agentregistration.shared.*
import uk.gov.hmrc.agentregistration.shared.BusinessType.*
import uk.gov.hmrc.agentregistration.shared.BusinessType.Partnership.*
import uk.gov.hmrc.agentregistration.shared.businessdetails.CompanyProfile
import uk.gov.hmrc.agentregistration.shared.businessdetails.FullName
import uk.gov.hmrc.agentregistration.shared.companieshouse.ChroAddress
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.FormatterFactory
import uk.gov.hmrc.agentregistrationfrontend.model.grs.*
import uk.gov.hmrc.agentregistrationfrontend.model.grs.RegistrationStatus.*
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.SafeIdGenerator
import uk.gov.hmrc.agentregistrationfrontend.testonly.services.GrsStubService
import uk.gov.hmrc.agentregistrationfrontend.testonly.views.html.GrsStub
import uk.gov.hmrc.domain.NinoGenerator
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.voa.play.form.ConditionalMappings.isEqual
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIf
import uk.gov.voa.play.form.conditionOpts

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrsStubController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  view: GrsStub,
  grsStubService: GrsStubService
)
extends FrontendController(mcc, actions):

  def showGrsData(
    businessType: BusinessType,
    journeyId: JourneyId
  ): Action[AnyContent] = actions
    .authorised:
      implicit request =>
        val prefilledForm =
          request.session.get(journeyId.value).map(data => Json.parse(data).as[JourneyData]) match {
            case Some(data) => form(businessType).fill(data)
            case _ => formWithDefaults(businessType)
          }
        Ok(view(
          prefilledForm,
          businessType,
          journeyId
        ))

  def submitGrsData(
    businessType: BusinessType,
    journeyId: JourneyId
  ): Action[AnyContent] = actions
    .authorised
    .ensureValidForm(
      form = form(businessType),
      resultToServeWhenFormHasErrors =
        implicit request =>
          view(
            _,
            businessType,
            journeyId
          )
    )
    .async:
      implicit request =>
        val journeyData: JourneyData = request.get
        val json: JsValue = Json.toJson(journeyData)
        val deceasedFlag: Boolean = extractDeceasedFlag

        for
          _ <- grsStubService.storeStubsData(
            businessType,
            journeyData,
            deceasedFlag
          )
        yield Redirect(AppRoutes.apply.internal.GrsController.journeyCallback(Some(journeyId)))
          .addingToSession(journeyId.value -> json.toString)

  private def extractDeceasedFlag(using request: Request[AnyContent]): Boolean = request.body.asFormUrlEncoded
    .flatMap(_.get("deceased").flatMap(_.headOption))
    .exists(v => v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on"))

  def retrieveGrsData(journeyId: JourneyId): Action[AnyContent] = actions.action: request =>
    request.session.get(journeyId.value) match {
      case Some(data) => Ok(data)
      case None => NotFound
    }

  def setupGrsJourney(businessType: BusinessType): Action[JourneyConfig] =
    actions.action(parse.json[JourneyConfig]): (_: Request[JourneyConfig]) =>
      Created(Json.obj(
        "journeyStartUrl" -> AppRoutes.testOnly.applicant.GrsStubController.showGrsData(businessType, randomJourneyId()).url
      ))

  val seed = 123456
  val utrGenerator = SaUtrGenerator(seed) // for our test-only purposes we can use SaUtrGenerator to generate both SA UTRs and CT UTRs
  val ninoGenerator = NinoGenerator(seed)
  def randomSaUtr(): SaUtr = SaUtr(utrGenerator.nextSaUtr.utr)
  def randomCtUtr(): CtUtr = CtUtr(utrGenerator.nextSaUtr.utr)
  def randomJourneyId(): JourneyId = JourneyId(UUID.randomUUID().toString)
  def randomNino(): Nino = Nino(ninoGenerator.nextNino.nino)

  private def form(businessType: BusinessType): Form[JourneyData] =
    val registrationStatusMapping: FieldMapping[RegistrationStatus] = Forms.of(using
      FormatterFactory.makeEnumFormatter[RegistrationStatus](
        errorMessageIfMissing = "Registration status required",
        errorMessageIfEnumError = "Registration status invalid"
      )
    )

    def sautrMapping(businessType: BusinessType): play.api.data.Mapping[Option[String]] =
      val partnershipTypes = Seq(
        GeneralPartnership,
        LimitedLiabilityPartnership,
        LimitedPartnership,
        ScottishLimitedPartnership,
        ScottishPartnership
      )
      if (partnershipTypes.contains(businessType))
        mandatoryIf(_ => true, nonEmptyText) // always mandatory for partnership types
      else
        optional(nonEmptyText) // optional (SoleTrader/others)

    Form(mapping(
      "registrationStatus" -> registrationStatusMapping,
      "safeId" -> mandatoryIf(
        isEqual("registrationStatus", GrsRegistered.toString),
        nonEmptyText
      ),
      "firstName" -> mandatoryIf(
        _ => businessType === SoleTrader,
        nonEmptyText
      ),
      "lastName" -> mandatoryIf(
        _ => businessType === SoleTrader,
        nonEmptyText
      ),
      "dateOfBirth" -> mandatoryIf(
        _ => businessType === SoleTrader,
        nonEmptyText
      ),
      "nino" -> mandatoryIf(
        isEqual("trn", "").and(_ => businessType === SoleTrader),
        nonEmptyText
      ),
      "trn" -> mandatoryIf(
        isEqual("nino", "").and(_ => businessType === SoleTrader),
        nonEmptyText
      ),
      "sautr" -> sautrMapping(businessType),
      "companyNumber" -> mandatoryIf(
        _ =>
          Seq(
            LimitedCompany,
            LimitedLiabilityPartnership,
            LimitedPartnership,
            ScottishLimitedPartnership
          ).contains(businessType),
        nonEmptyText
      ),
      "companyName" -> mandatoryIf(
        _ =>
          Seq(
            LimitedCompany,
            LimitedLiabilityPartnership,
            LimitedPartnership,
            ScottishLimitedPartnership
          ).contains(businessType),
        nonEmptyText
      ),
      "dateOfIncorporation" -> optional(nonEmptyText),
      "ctutr" -> mandatoryIf(
        _ => businessType === LimitedCompany,
        nonEmptyText
      ),
      "postcode" -> mandatoryIf(
        _ =>
          Seq(
            GeneralPartnership,
            LimitedLiabilityPartnership,
            LimitedPartnership,
            ScottishLimitedPartnership,
            ScottishPartnership
          ).contains(businessType),
        nonEmptyText
      )
    )(
      (
        status,
        safeId,
        firstName,
        lastName,
        dateOfBirth,
        nino,
        trn,
        sautr,
        companyNumber,
        companyName,
        dateOfIncorporation,
        ctutr,
        postcode
      ) =>
        JourneyData(
          identifiersMatch = if status === GrsNotCalled then false else true,
          registration = Registration(
            registrationStatus = status,
            registeredBusinessPartnerId = safeId.map(SafeId.apply)
          ),
          fullName = firstName.map(first => FullName(first, lastName.getOrElse(""))),
          dateOfBirth = dateOfBirth.map(LocalDate.parse),
          nino = nino.map(Nino.apply),
          trn = trn,
          sautr = sautr.map(SaUtr.apply),
          // address = ... when/if adding support for SoleTrader address, don't add it to the stub page and just hardcode it here when trn is defined
          companyProfile = companyNumber.map(crn =>
            CompanyProfile(
              companyNumber = Crn(crn),
              companyName = companyName.getOrElse(""),
              dateOfIncorporation = dateOfIncorporation.map(LocalDate.parse),
              unsanitisedCHROAddress = Some(ChroAddress(
                address_line_1 = Some("23 Great Portland Street"),
                address_line_2 = Some("London"),
                country = Some("United Kingdom"),
                postal_code = Some("W1 1AQ")
              ))
            )
          ),
          ctutr = ctutr.map(CtUtr.apply),
          postcode = postcode
        )
    )(response =>
      Some((
        response.registration.registrationStatus,
        response.registration.registeredBusinessPartnerId.map(_.value),
        response.fullName.map(_.firstName),
        response.fullName.map(_.lastName),
        response.dateOfBirth.map(_.toString),
        response.nino.map(_.value),
        response.trn,
        response.sautr.map(_.value),
        response.companyProfile.map(_.companyNumber.value),
        response.companyProfile.map(_.companyName),
        response.companyProfile.flatMap(_.dateOfIncorporation.map(_.toString)),
        response.ctutr.map(_.value),
        response.postcode
      ))
    ))

  private def formWithDefaults(businessType: BusinessType): Form[JourneyData] = form(businessType).fill(JourneyData(
    identifiersMatch = true,
    registration = Registration(
      registrationStatus = GrsRegistered,
      registeredBusinessPartnerId = Some(SafeIdGenerator.generateSafeId())
    ),
    fullName = if businessType === SoleTrader then Some(FullName("Test", "User")) else None,
    dateOfBirth = if businessType === SoleTrader then Some(LocalDate.now().minusYears(20)) else None,
    nino = if businessType === SoleTrader then Some(randomNino()) else None,
    trn = None,
    sautr =
      if Seq(
          SoleTrader,
          GeneralPartnership,
          LimitedLiabilityPartnership,
          ScottishPartnership,
          LimitedPartnership,
          ScottishLimitedPartnership
        ).contains(businessType)
      then Some(randomSaUtr())
      else None,
    companyProfile =
      if Seq(
          LimitedCompany,
          LimitedLiabilityPartnership,
          LimitedPartnership,
          ScottishLimitedPartnership
        ).contains(businessType)
      then
        Some(CompanyProfile(
          companyNumber = companyNumberForBusinessType(businessType),
          companyName = if businessType === LimitedCompany then "Test Company Ltd" else "Test Partnership",
          dateOfIncorporation = Some(LocalDate.now().minusYears(10)),
          unsanitisedCHROAddress = Some(ChroAddress(
            address_line_1 = Some("23 Great Portland Street"),
            address_line_2 = Some("London"),
            country = Some("United Kingdom"),
            postal_code = Some("W1 1AQ")
          ))
        ))
      else None,
    ctutr = if businessType === LimitedCompany then Some(randomCtUtr()) else None,
    postcode =
      if Seq(
          GeneralPartnership,
          LimitedLiabilityPartnership,
          LimitedPartnership,
          ScottishLimitedPartnership,
          ScottishPartnership
        ).contains(businessType)
      then Some("AA1 1AA")
      else None
  ))

  // these defaults are to match any stubs we have used for Companies House lookups including officer searches
  private def companyNumberForBusinessType(businessType: BusinessType): Crn =
    businessType match
      case LimitedCompany => Crn("11111111")
      case LimitedLiabilityPartnership => Crn("22222222")
      case LimitedPartnership => Crn("33333333")
      case ScottishLimitedPartnership => Crn("44444444")
      case _ => Crn("12345678") // we don't have a matching CH stub for any other business type so return the original default

/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.pki.gemlibpki.certificate;

import static de.gematik.pki.gemlibpki.TestConstants.FILE_NAME_TSL_ECC_DEFAULT;
import static de.gematik.pki.gemlibpki.TestConstants.LOCAL_SSP_DIR;
import static de.gematik.pki.gemlibpki.TestConstants.OCSP_HOST;
import static de.gematik.pki.gemlibpki.TestConstants.PRODUCT_TYPE;
import static de.gematik.pki.gemlibpki.TestConstants.VALID_ISSUER_CERT_EGK;
import static de.gematik.pki.gemlibpki.TestConstants.VALID_ISSUER_CERT_HBA;
import static de.gematik.pki.gemlibpki.TestConstants.VALID_ISSUER_CERT_KOMP_CA10;
import static de.gematik.pki.gemlibpki.TestConstants.VALID_ISSUER_CERT_KOMP_CA50;
import static de.gematik.pki.gemlibpki.TestConstants.VALID_ISSUER_CERT_KOMP_CA54;
import static de.gematik.pki.gemlibpki.TestConstants.VALID_ISSUER_CERT_SMCB;
import static de.gematik.pki.gemlibpki.TestConstants.VALID_ISSUER_CERT_SMCB_RSA;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_CH_AUT_ECC;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_FD_OSIG;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_FD_SIG;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_HCI_AUT_ECC;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_HCI_AUT_RSA;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_HCI_OSIG;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_HP_AUT_ECC;
import static de.gematik.pki.gemlibpki.certificate.CertificateProfile.CERT_PROFILE_C_TSL_SIG;
import static de.gematik.pki.gemlibpki.utils.TestUtils.overwriteSspUrls;
import static de.gematik.pki.gemlibpki.utils.TestUtils.readCert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import de.gematik.pki.gemlibpki.common.OcspResponderMock;
import de.gematik.pki.gemlibpki.error.ErrorCode;
import de.gematik.pki.gemlibpki.exception.GemPkiException;
import de.gematik.pki.gemlibpki.exception.GemPkiParsingException;
import de.gematik.pki.gemlibpki.exception.GemPkiRuntimeException;
import de.gematik.pki.gemlibpki.ocsp.OcspConstants;
import de.gematik.pki.gemlibpki.ocsp.OcspRequestGenerator;
import de.gematik.pki.gemlibpki.ocsp.OcspRespCache;
import de.gematik.pki.gemlibpki.ocsp.OcspResponseGenerator;
import de.gematik.pki.gemlibpki.ocsp.OcspTestConstants;
import de.gematik.pki.gemlibpki.tsl.TslInformationProvider;
import de.gematik.pki.gemlibpki.tsl.TspInformationProvider;
import de.gematik.pki.gemlibpki.tsl.TspService;
import de.gematik.pki.gemlibpki.tsl.TspServiceSubset;
import de.gematik.pki.gemlibpki.utils.CertificateProvider;
import de.gematik.pki.gemlibpki.utils.GemlibPkiUtils;
import de.gematik.pki.gemlibpki.utils.TestUtils;
import de.gematik.pki.gemlibpki.utils.VariableSource;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.assertj.core.api.AbstractThrowableAssert;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

class TucPki018VerifierTest {
  private static final X509Certificate VALID_X509_EE_CERT =
      readCert("GEM.SMCB-CA10/valid/DrMedGunther.pem");
  private static final CertificateProfile certificateProfile = CERT_PROFILE_C_HCI_AUT_ECC;
  private static final List<CertificateProfile> certificateProfiles = List.of(certificateProfile);
  private static final OcspResponderMock ocspResponderMock =
      new OcspResponderMock(LOCAL_SSP_DIR, OCSP_HOST);
  private static final int ocspTimeoutSeconds = OcspConstants.DEFAULT_OCSP_TIMEOUT_SECONDS;

  private TucPki018Verifier tucPki018Verifier;
  private OcspRespCache ocspRespCache;

  private boolean tolerateOcspFailure;

  @BeforeEach
  void init() {
    ocspRespCache = new OcspRespCache(30);

    tolerateOcspFailure = false;
    tucPki018Verifier = buildTucPki18Verifier(certificateProfiles);
  }

  private TucPki018Verifier buildTucPki18Verifier(
      final List<CertificateProfile> certificateProfiles) {

    final List<TspService> tspServiceList = TestUtils.getDefaultTspServiceList();

    overwriteSspUrls(tspServiceList, ocspResponderMock.getSspUrl());

    return TucPki018Verifier.builder()
        .productType(PRODUCT_TYPE)
        .tspServiceList(tspServiceList)
        .certificateProfiles(certificateProfiles)
        .ocspRespCache(ocspRespCache)
        .ocspTimeoutSeconds(ocspTimeoutSeconds)
        .tolerateOcspFailure(tolerateOcspFailure)
        .build();
  }

  @Test
  void verifyPerformTucPki18ChecksValid() {

    ocspResponderMock.configureForOcspRequest(VALID_X509_EE_CERT, VALID_ISSUER_CERT_SMCB);
    assertDoesNotThrow(() -> tucPki018Verifier.performTucPki18Checks(VALID_X509_EE_CERT));
  }

  @Test
  void verifyPerformTucPki18ChecksWithoutOcsp() {
    final List<TspService> tspServiceList = TestUtils.getDefaultTspServiceList();
    overwriteSspUrls(tspServiceList, "invalidSsp");
    final TucPki018Verifier verifier =
        TucPki018Verifier.builder()
            .productType(PRODUCT_TYPE)
            .tspServiceList(tspServiceList)
            .certificateProfiles(certificateProfiles)
            .ocspRespCache(ocspRespCache)
            .withOcspCheck(false)
            .build();
    assertDoesNotThrow(() -> verifier.performTucPki18Checks(VALID_X509_EE_CERT));
  }

  @Test
  void verifyEgkAutEccCertValid() {
    final X509Certificate eeCert = readCert("GEM.EGK-CA10/JunaFuchs.pem");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_EGK);
    assertDoesNotThrow(
        () ->
            buildTucPki18Verifier(List.of(CERT_PROFILE_C_CH_AUT_ECC))
                .performTucPki18Checks(eeCert));
  }

  @Test
  void verifyHbaAutEccCertValid() {
    final X509Certificate eeCert = readCert("GEM.HBA-CA13/GüntherOtís.pem");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_HBA);
    assertDoesNotThrow(
        () ->
            buildTucPki18Verifier(List.of(CERT_PROFILE_C_HP_AUT_ECC))
                .performTucPki18Checks(eeCert));
  }

  @Test
  void verifySmcbAutRsaCertValid() {
    final X509Certificate eeCert = readCert("GEM.SMCB-CA24-RSA/AschoffscheApotheke.pem");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_SMCB_RSA);
    assertDoesNotThrow(
        () ->
            buildTucPki18Verifier(List.of(CERT_PROFILE_C_HCI_AUT_RSA))
                .performTucPki18Checks(eeCert));
  }

  @Test
  void verifySigDCertValid() {
    final X509Certificate eeCert = readCert("GEM.KOMP-CA10/c.fd.sig_keyUsage_digiSig.pem");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_KOMP_CA10);
    assertDoesNotThrow(
        () -> buildTucPki18Verifier(List.of(CERT_PROFILE_C_FD_SIG)).performTucPki18Checks(eeCert));
  }

  @Test
  void verifySmcbOsigRsaCertValid() {
    final X509Certificate eeCert = readCert("GEM.SMCB-CA24-RSA/c-hci-osig_apo.valid.crt");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_SMCB_RSA);
    assertDoesNotThrow(
        () ->
            buildTucPki18Verifier(List.of(CERT_PROFILE_C_HCI_OSIG)).performTucPki18Checks(eeCert));
  }

  @Test
  void verifyFdOsigRsaCertValid() {
    final X509Certificate eeCert = readCert("GEM.KOMP-CA50/erzpecc.pem");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_KOMP_CA50);
    assertDoesNotThrow(
        () -> buildTucPki18Verifier(List.of(CERT_PROFILE_C_FD_OSIG)).performTucPki18Checks(eeCert));
  }

  @Test
  void verifyFdOsigEccCertValid() {
    final X509Certificate eeCert = readCert("GEM.KOMP-CA54/erzprsa.pem");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_KOMP_CA54);
    assertDoesNotThrow(
        () -> buildTucPki18Verifier(List.of(CERT_PROFILE_C_FD_OSIG)).performTucPki18Checks(eeCert));
  }

  @Test
  void verifyProfessionOidsValid() throws GemPkiException {
    final X509Certificate eeCert = readCert("GEM.SMCB-CA24-RSA/c-hci-osig_apo.valid.crt");
    ocspResponderMock.configureForOcspRequest(eeCert, VALID_ISSUER_CERT_SMCB_RSA);
    assertThat(
            buildTucPki18Verifier(List.of(CERT_PROFILE_C_HCI_OSIG))
                .performTucPki18Checks(eeCert)
                .getProfessionOids())
        .contains(Role.OID_OEFFENTLICHE_APOTHEKE.getProfessionOid());
  }

  @Test
  void verifyNotEveryKeyUsagePresent() {
    final X509Certificate ASCHOFFSCHE_APOTHEKE_PEM =
        readCert("GEM.SMCB-CA24-RSA/AschoffscheApotheke.pem");
    ocspResponderMock.configureForOcspRequest(ASCHOFFSCHE_APOTHEKE_PEM, VALID_ISSUER_CERT_SMCB_RSA);
    assertThatThrownBy(() -> tucPki018Verifier.performTucPki18Checks(ASCHOFFSCHE_APOTHEKE_PEM))
        .isInstanceOf(GemPkiParsingException.class)
        .hasMessageContaining(ErrorCode.SE_1016_WRONG_KEYUSAGE.name());
  }

  @Test
  void multipleCertificateProfiles_shouldSelectCorrectOne() {
    ocspResponderMock.configureForOcspRequest(VALID_X509_EE_CERT, VALID_ISSUER_CERT_SMCB);
    assertDoesNotThrow(
        () ->
            buildTucPki18Verifier(
                    List.of(
                        CERT_PROFILE_C_TSL_SIG,
                        CERT_PROFILE_C_HCI_AUT_RSA,
                        CERT_PROFILE_C_HCI_AUT_ECC))
                .performTucPki18Checks(VALID_X509_EE_CERT));
  }

  @Test
  void multipleCertificateProfiles_shouldThrowKeyUsageError() {
    final X509Certificate eeWrongKeyUsage =
        readCert("GEM.SMCB-CA10/invalid/DrMedGunther_invalid-keyusage.pem");
    ocspResponderMock.configureForOcspRequest(eeWrongKeyUsage, VALID_ISSUER_CERT_SMCB);
    final TucPki018Verifier verifier =
        buildTucPki18Verifier(List.of(CERT_PROFILE_C_HCI_AUT_ECC, CERT_PROFILE_C_HP_AUT_ECC));
    final AbstractThrowableAssert<?, ? extends Throwable> z =
        assertThatThrownBy(() -> verifier.performTucPki18Checks(eeWrongKeyUsage))
            .isInstanceOf(GemPkiParsingException.class)
            .hasMessageContaining(ErrorCode.SE_1016_WRONG_KEYUSAGE.name());
  }

  @Test
  void multipleCertificateProfiles_shouldThrowCertTypeError() {
    final X509Certificate eeWrongKeyUsage =
        readCert("GEM.SMCB-CA10/invalid/DrMedGunther_invalid-certificate-type.pem");
    ocspResponderMock.configureForOcspRequest(eeWrongKeyUsage, VALID_ISSUER_CERT_SMCB);
    final TucPki018Verifier verifier =
        buildTucPki18Verifier(List.of(CERT_PROFILE_C_HCI_AUT_ECC, CERT_PROFILE_C_HP_AUT_ECC));
    assertThatThrownBy(() -> verifier.performTucPki18Checks(eeWrongKeyUsage))
        .isInstanceOf(GemPkiParsingException.class)
        .hasMessageContaining(ErrorCode.SE_1018_CERT_TYPE_MISMATCH.name())
        .hasMessageContaining(ErrorCode.SE_1016_WRONG_KEYUSAGE.name());
  }

  @Test
  void nonNullTests() throws GemPkiException {

    assertThatThrownBy(() -> tucPki018Verifier.performTucPki18Checks(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("x509EeCert is marked non-null but is null");

    assertThatThrownBy(() -> buildTucPki18Verifier(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("certificateProfiles is marked non-null but is null");

    final TspServiceSubset tspServiceSubset =
        new TspInformationProvider(
                new TslInformationProvider(TestUtils.getTsl(FILE_NAME_TSL_ECC_DEFAULT))
                    .getTspServices(),
                PRODUCT_TYPE)
            .getIssuerTspServiceSubset(VALID_X509_EE_CERT);

    assertThatThrownBy(() -> tucPki018Verifier.tucPki018ProfileChecks(null, tspServiceSubset))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("x509EeCert is marked non-null but is null");

    assertThatThrownBy(() -> tucPki018Verifier.tucPki018ProfileChecks(VALID_X509_EE_CERT, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("tspServiceSubset is marked non-null but is null");

    assertThatThrownBy(
            () ->
                tucPki018Verifier.tucPki018ChecksForProfile(
                    null, certificateProfile, tspServiceSubset))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("x509EeCert is marked non-null but is null");
    assertThatThrownBy(
            () ->
                tucPki018Verifier.tucPki018ChecksForProfile(
                    VALID_X509_EE_CERT, null, tspServiceSubset))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("certificateProfile is marked non-null but is null");

    assertThatThrownBy(
            () ->
                tucPki018Verifier.tucPki018ChecksForProfile(
                    VALID_X509_EE_CERT, certificateProfile, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("tspServiceSubset is marked non-null but is null");

    assertThatThrownBy(() -> tucPki018Verifier.commonChecks(null, tspServiceSubset))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("x509EeCert is marked non-null but is null");

    assertThatThrownBy(() -> tucPki018Verifier.commonChecks(VALID_X509_EE_CERT, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("tspServiceSubset is marked non-null but is null");

    final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    assertThatThrownBy(() -> tucPki018Verifier.doOcspIfConfigured(null, tspServiceSubset, now))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("x509EeCert is marked non-null but is null");

    assertThatThrownBy(() -> tucPki018Verifier.doOcspIfConfigured(VALID_X509_EE_CERT, null, now))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("tspServiceSubset is marked non-null but is null");

    assertThatThrownBy(
            () -> tucPki018Verifier.doOcspIfConfigured(VALID_X509_EE_CERT, tspServiceSubset, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("referenceDate is marked non-null but is null");
  }

  @Test
  void verifyCertProfilesEmpty() {
    ocspResponderMock.configureForOcspRequest(VALID_X509_EE_CERT, VALID_ISSUER_CERT_SMCB);
    final TucPki018Verifier verifier = buildTucPki18Verifier(List.of());
    assertThatThrownBy(() -> verifier.performTucPki18Checks(VALID_X509_EE_CERT))
        .isInstanceOf(GemPkiRuntimeException.class)
        .hasMessage("Liste der konfigurierten Zertifikatsprofile ist leer.");
  }

  @ParameterizedTest
  @ArgumentsSource(CertificateProvider.class)
  @VariableSource(value = "valid")
  void verifyPerformTucPki18ChecksValid(final X509Certificate cert) {
    ocspResponderMock.configureForOcspRequest(cert, VALID_ISSUER_CERT_SMCB);
    assertDoesNotThrow(() -> tucPki018Verifier.performTucPki18Checks(cert));
  }

  @ParameterizedTest
  @ArgumentsSource(CertificateProvider.class)
  @VariableSource(value = "invalid")
  void verifyPerformTucPki18ChecksInvalid(final X509Certificate cert) {
    ocspResponderMock.configureForOcspRequest(cert, VALID_ISSUER_CERT_SMCB);
    assertThatThrownBy(() -> tucPki018Verifier.performTucPki18Checks(cert))
        .as("Test invalid certificates")
        .isInstanceOf(GemPkiException.class);
  }

  @Test
  void verifyPerformTucPki18ChecksOcspTimeoutZeroSeconds() {

    ocspResponderMock.configureForOcspRequest(VALID_X509_EE_CERT, VALID_ISSUER_CERT_SMCB);

    final List<TspService> tspServiceList = TestUtils.getDefaultTspServiceList();

    overwriteSspUrls(tspServiceList, ocspResponderMock.getSspUrl());

    final TucPki018Verifier verifier =
        TucPki018Verifier.builder()
            .productType(PRODUCT_TYPE)
            .tspServiceList(tspServiceList)
            .certificateProfiles(certificateProfiles)
            .ocspRespCache(ocspRespCache)
            .ocspTimeoutSeconds(0)
            .tolerateOcspFailure(false)
            .build();

    assertThatThrownBy(() -> verifier.performTucPki18Checks(VALID_X509_EE_CERT))
        .isInstanceOf(GemPkiException.class)
        .hasMessage(ErrorCode.TE_1032_OCSP_NOT_AVAILABLE.getErrorMessage(PRODUCT_TYPE));
  }

  @Test
  void verifyPerformTucPki18ChecksWithGivenOcspResponseValid() {

    final ZonedDateTime referenceDate = GemlibPkiUtils.now().minusYears(10);

    final OCSPReq ocspReq =
        OcspRequestGenerator.generateSingleOcspRequest(VALID_X509_EE_CERT, VALID_ISSUER_CERT_SMCB);

    final OCSPResp ocspResp =
        OcspResponseGenerator.builder()
            .signer(OcspTestConstants.getOcspSignerEcc())
            .producedAt(referenceDate)
            .nextUpdate(referenceDate)
            .thisUpdate(referenceDate)
            .build()
            .generate(ocspReq, VALID_X509_EE_CERT);

    final List<TspService> tspServiceList = TestUtils.getDefaultTspServiceList();

    final TucPki018Verifier verifier =
        TucPki018Verifier.builder()
            .productType(PRODUCT_TYPE)
            .tspServiceList(tspServiceList)
            .certificateProfiles(certificateProfiles)
            .ocspResponse(ocspResp)
            .build();

    assertDoesNotThrow(() -> verifier.performTucPki18Checks(VALID_X509_EE_CERT, referenceDate));
  }

  @Test
  void verifyPerformTucPki18ChecksWithGivenOcspResponseInvalidAndOnlineResponseValid() {

    ocspResponderMock.configureForOcspRequest(VALID_X509_EE_CERT, VALID_ISSUER_CERT_SMCB);

    final ZonedDateTime referenceDate = GemlibPkiUtils.now().minusYears(10);

    final OCSPReq ocspReq =
        OcspRequestGenerator.generateSingleOcspRequest(VALID_X509_EE_CERT, VALID_ISSUER_CERT_SMCB);

    final OCSPResp ocspResp =
        OcspResponseGenerator.builder()
            .signer(OcspTestConstants.getOcspSignerEcc())
            .producedAt(referenceDate)
            .nextUpdate(referenceDate)
            .thisUpdate(referenceDate)
            .build()
            .generate(ocspReq, VALID_X509_EE_CERT);

    final List<TspService> tspServiceList = TestUtils.getDefaultTspServiceList();

    overwriteSspUrls(tspServiceList, ocspResponderMock.getSspUrl());

    final TucPki018Verifier verifier =
        TucPki018Verifier.builder()
            .productType(PRODUCT_TYPE)
            .tspServiceList(tspServiceList)
            .certificateProfiles(certificateProfiles)
            .ocspResponse(ocspResp)
            .build();

    // TECHNICAL_WARNING TW_1050_PROVIDED_OCSP_RESPONSE_NOT_VALID
    assertDoesNotThrow(() -> verifier.performTucPki18Checks(VALID_X509_EE_CERT));
  }
}
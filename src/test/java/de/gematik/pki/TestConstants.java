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

package de.gematik.pki;

public class TestConstants {

    public static final String PRODUCT_TYPE = "Unittest";
    // TODO why many TSLs (TSL-test.xml)
    public static final String FILE_NAME_TSL_DEFAULT = "tsls/valid/TSL_default.xml";
    public static final String FILE_NAME_TSL_ALT_CA = "tsls/valid/TSL_altCA.xml";
    public final static String LOCAL_SSP_DIR = "/services/ocsp";
    public final static String OCSP_HOST = "http://localhost:";

}

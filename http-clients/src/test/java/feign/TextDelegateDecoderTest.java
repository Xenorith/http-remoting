/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package feign;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.http.FeignClients;
import feign.codec.Decoder;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class TextDelegateDecoderTest {
    private static final String DELEGATE_RESPONSE = "delegate response";

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TestServer.class,
            "src/test/resources/test-server.yml");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private TestServer.TestService service;
    private Map<String, Collection<String>> headers;
    private Decoder delegate;
    private Decoder textDelegateDecoder;

    @Before
    public void before() {
        delegate = mock(Decoder.class);
        headers = Maps.newHashMap();
        textDelegateDecoder = new TextDelegateDecoder(delegate);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(), endpointUri,
                TestServer.TestService.class);
    }

    @Test
    public void testUsesStringDecoderWithTextPlain() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.TEXT_PLAIN));
        Response response = Response.create(200, "OK", headers, "text response", Charsets.UTF_8);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertEquals(decodedObject, "text response");
        verifyZeroInteractions(delegate);
    }

    @Test
    public void testUsesDelegateWithNoHeader() throws Exception {
        when(delegate.decode((Response) any(), (Type) any())).thenReturn(DELEGATE_RESPONSE);
        Response response = Response.create(200, "OK", headers, new byte[0]);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertEquals(decodedObject, DELEGATE_RESPONSE);
    }

    @Test
    public void testUsesDelegateWithComplexHeader() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
        when(delegate.decode((Response) any(), (Type) any())).thenReturn(DELEGATE_RESPONSE);
        Response response = Response.create(200, "OK", headers, new byte[0]);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertEquals(decodedObject, DELEGATE_RESPONSE);
    }

    @Test
    public void testUsesDelegateWithNonTextContentType() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.APPLICATION_JSON));
        when(delegate.decode((Response) any(), (Type) any())).thenReturn(DELEGATE_RESPONSE);
        Response response = Response.create(200, "OK", headers, new byte[0]);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertEquals(decodedObject, DELEGATE_RESPONSE);
    }

    @Test
    public void testStandardClientsUseTextDelegateEncoder() {
        assertThat(service.getString("string"), is("string"));
        assertThat(service.getString(null), is((String) null));
    }

    @Test
    public void testInterplayOfOptionalAwareDecoderAndTextDelegateDecoder() {
        assertNull(service.getString(null));

        Optional<String> result = service.getOptionalString("string");
        assertEquals(Optional.of("string"), result);

        try {
            service.getOptionalString(null);
            fail();
        } catch (NotFoundException e) {
            assertThat(e.getMessage(), containsString("HTTP 404 Not Found"));
        }
    }
}

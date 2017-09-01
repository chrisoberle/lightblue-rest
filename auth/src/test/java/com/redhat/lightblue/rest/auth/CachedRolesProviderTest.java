package com.redhat.lightblue.rest.auth;

import com.redhat.lightblue.rest.auth.health.RolesProviderHealth;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class CachedRolesProviderTest {

    @Mock
    RolesProvider rolesProvider;

    @Spy
    RolesCache rolesCache = new RolesCache(1000);

    CachedRolesProvider cachedRolesProvider;

    final Set<String> roles = new HashSet<String>(Arrays.asList(new String[]{"role1","role2"}));

    @Before
    public void init() {
        cachedRolesProvider = new CachedRolesProvider(rolesProvider, rolesCache);
    }

    @Test
    public void testRolesCache() throws Exception {

        Mockito.when(rolesProvider.getUserRoles("user")).thenReturn(roles);

        // cache miss
        Set<String> returnedRoles = cachedRolesProvider.getUserRoles("user");
        Assert.assertEquals(roles, returnedRoles);
        // cache hit
        returnedRoles = cachedRolesProvider.getUserRoles("user");
        Assert.assertEquals(roles, returnedRoles);
        Thread.sleep(1000);
        // cache miss (expired)
        returnedRoles = cachedRolesProvider.getUserRoles("user");
        Assert.assertEquals(roles, returnedRoles);

        // checked rolesCache for user 3 times
        Mockito.verify(rolesCache, Mockito.times(3)).get("user");
        // populated cache twice
        Mockito.verify(rolesCache, Mockito.times(2)).put("user", roles);
        // 2 remote calls, because user was found in cache once
        Mockito.verify(rolesProvider, Mockito.times(2)).getUserRoles("user");
        // fallback cache was not used
        Mockito.verify(rolesCache, Mockito.times(0)).getFromFallback("user");
    }

    @Test
    public void testFallBackRolesCache() throws Exception {

        // ldap failure
        Mockito.when(rolesProvider.getUserRoles("user")).thenThrow(new LDAPException(ResultCode.SERVER_DOWN));
 
        rolesCache.put("user", roles);
        Thread.sleep(1000); // wait till it expires
        Assert.assertNull("rolesCache should have expired", rolesCache.get("user"));

        Set<String> returnedRoles = cachedRolesProvider.getUserRoles("user");
        Assert.assertEquals(roles, returnedRoles);
        Mockito.verify(rolesCache, Mockito.times(1)).getFromFallback("user");

    }
    
    @Test
    public void isHealthyIfDelegateRolesProviderIsHealthy() throws Exception {

        // Mock RolesProvider healthy
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mock", "Return healthy for a mock test");

        Mockito.when(rolesProvider.checkHealth()).thenReturn(new RolesProviderHealth(true, details));
        
        System.out.println(cachedRolesProvider.checkHealth().details());        
        Assert.assertTrue(cachedRolesProvider.checkHealth().isHealthy());

    }
    
    @Test
    public void isUnhealthyIfDelegateRolesProviderIsUnhealthy() throws Exception {

        // Mock RolesProvider unhealthy
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mock", "Return unhealthy for a mock test");

        Mockito.when(rolesProvider.checkHealth()).thenReturn(new RolesProviderHealth(false, details));
        
        System.out.println(cachedRolesProvider.checkHealth().details());        
        Assert.assertFalse(cachedRolesProvider.checkHealth().isHealthy());

    }
}

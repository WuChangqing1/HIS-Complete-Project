package com.example.his;

import com.example.his.auth.AuthService;
import com.example.his.core.ApiError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuestAccessTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final AuthService auth = new AuthService(db);
    private final Map<String,Object> guest = Map.of("id",7L,"username","demo_guest","realname","游客","role","ADMIN");

    @Test void disabledUnlessBothSwitchesAreEnabled() {
        ReflectionTestUtils.setField(auth,"guestAccess",true);
        assertFalse(auth.guestEnabled());
        assertEquals(403,assertThrows(ApiError.class,auth::enterGuest).status());
        verifyNoInteractions(db);
    }

    @Test void dedicatedGuestSessionHasAdminRoleAndAuditableActor() {
        ReflectionTestUtils.setField(auth,"guestAccess",true);
        ReflectionTestUtils.setField(auth,"demo",true);
        when(db.queryForList(contains("role='ADMIN'"),eq("demo_guest"))).thenReturn(List.of(guest));
        var session=auth.enterGuest();
        assertEquals("ADMIN",((Map<?,?>)session.get("user")).get("role"));
        assertTrue(session.get("token").toString().matches("[a-f0-9]{64}"));
        verify(db).update(contains("INSERT INTO audit_log"),eq(7L),eq("GUEST_LOGIN"),eq("employee:7"),eq(""));
    }

    @Test void disablingGuestRejectsPreviouslyIssuedGuestSessions() {
        when(db.queryForList(contains("auth_session"),anyString())).thenReturn(List.of(guest));
        var request=new MockHttpServletRequest();
        request.addHeader("Authorization","Bearer old-token");
        assertEquals(401,assertThrows(ApiError.class,()->auth.current(request)).status());
    }
}

package org.mifosplatform.useradministration.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang.StringUtils;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.domain.AbstractAuditableCustom;
import org.mifosplatform.infrastructure.security.domain.PlatformUser;
import org.mifosplatform.infrastructure.security.exception.NoAuthorizationException;
import org.mifosplatform.infrastructure.security.service.PlatformPasswordEncoder;
import org.mifosplatform.organisation.office.domain.Office;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

@Entity
@Table(name = "m_appuser", uniqueConstraints = @UniqueConstraint(columnNames = { "username" }, name = "username_org"))
public class AppUser extends AbstractAuditableCustom<AppUser, Long> implements PlatformUser {

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "firstname", nullable = false, length = 100)
    private String firstname;

    @Column(name = "lastname", nullable = false, length = 100)
    private String lastname;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "nonexpired", nullable = false)
    private boolean accountNonExpired;

    @Column(name = "nonlocked", nullable = false)
    private final boolean accountNonLocked;

    @Column(name = "nonexpired_credentials", nullable = false)
    private final boolean credentialsNonExpired;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @SuppressWarnings("unused")
    @Column(name = "firsttime_login_remaining", nullable = false)
    private boolean firstTimeLoginRemaining;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @ManyToOne
    @JoinColumn(name = "office_id")
    private Office office;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "m_appuser_role", joinColumns = @JoinColumn(name = "appuser_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;

    public static AppUser fromJson(final Office userOffice, final Set<Role> allRoles, final JsonCommand command) {

        final String username = command.stringValueOfParameterNamed("username");
        String password = command.stringValueOfParameterNamed("password");
        if (StringUtils.isBlank(password)) {
            password = "autogenerate";
        }

        boolean userEnabled = true;
        boolean userAccountNonExpired = true;
        boolean userCredentialsNonExpired = true;
        boolean userAccountNonLocked = true;

        Collection<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION"));

        User user = new User(username, password, userEnabled, userAccountNonExpired, userCredentialsNonExpired, userAccountNonLocked,
                authorities);

        final String email = command.stringValueOfParameterNamed("email");
        final String firstname = command.stringValueOfParameterNamed("firstname");
        final String lastname = command.stringValueOfParameterNamed("lastname");

        return new AppUser(userOffice, user, allRoles, email, firstname, lastname);
    }

    protected AppUser() {
        this.accountNonLocked = false;
        this.credentialsNonExpired = false;
        this.roles = new HashSet<Role>();
    }

    public AppUser(final Office office, final User user, final Set<Role> roles, final String email, final String firstname,
            final String lastname) {
        this.office = office;
        this.email = email.trim();
        this.username = user.getUsername().trim();
        this.firstname = firstname.trim();
        this.lastname = lastname.trim();
        this.password = user.getPassword().trim();
        this.accountNonExpired = user.isAccountNonExpired();
        this.accountNonLocked = user.isAccountNonLocked();
        this.credentialsNonExpired = user.isCredentialsNonExpired();
        this.enabled = user.isEnabled();
        this.roles = roles;
        this.firstTimeLoginRemaining = true;
    }

    public void updatePassword(final String encodePassword) {
        this.password = encodePassword;
        this.firstTimeLoginRemaining = false;
    }

    public void changeOffice(final Office differentOffice) {
        this.office = differentOffice;
    }

    public void updateRoles(final Set<Role> allRoles) {
        if (!allRoles.isEmpty()) {
            this.roles.clear();
            this.roles = allRoles;
        }
    }

    public Map<String, Object> update(final JsonCommand command, final PlatformPasswordEncoder platformPasswordEncoder) {

        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>(7);

        // unencoded password provided
        final String passwordParamName = "password";
        final String passwordEncodedParamName = "passwordEncoded";
        if (command.hasParameter(passwordParamName)) {
            if (command.isChangeInPasswordParameterNamed(passwordParamName, this.password, platformPasswordEncoder, this.getId())) {
                final String passwordEncodedValue = command.passwordValueOfParameterNamed(passwordParamName, platformPasswordEncoder,
                        this.getId());
                actualChanges.put(passwordEncodedParamName, passwordEncodedValue);
                updatePassword(passwordEncodedValue);
            }
        }

        if (command.hasParameter(passwordEncodedParamName)) {
            if (command.isChangeInStringParameterNamed(passwordEncodedParamName, this.password)) {
                final String newValue = command.stringValueOfParameterNamed(passwordEncodedParamName);
                actualChanges.put(passwordEncodedParamName, newValue);
                updatePassword(newValue);
            }
        }

        final String officeIdParamName = "officeId";
        if (command.isChangeInLongParameterNamed(officeIdParamName, this.office.getId())) {
            final Long newValue = command.longValueOfParameterNamed(officeIdParamName);
            actualChanges.put(officeIdParamName, newValue);
        }

        final String rolesParamName = "roles";
        if (command.isChangeInArrayParameterNamed(rolesParamName, getRolesAsIdStringArray())) {
            final String[] newValue = command.arrayValueOfParameterNamed(rolesParamName);
            actualChanges.put(rolesParamName, newValue);
        }

        final String usernameParamName = "username";
        if (command.isChangeInStringParameterNamed(usernameParamName, this.username)) {
            final String newValue = command.stringValueOfParameterNamed(usernameParamName);
            actualChanges.put(usernameParamName, newValue);
            this.username = newValue;
        }

        final String firstnameParamName = "firstname";
        if (command.isChangeInStringParameterNamed(firstnameParamName, this.firstname)) {
            final String newValue = command.stringValueOfParameterNamed(firstnameParamName);
            actualChanges.put(firstnameParamName, newValue);
            this.firstname = newValue;
        }

        final String lastnameParamName = "lastname";
        if (command.isChangeInStringParameterNamed(lastnameParamName, this.lastname)) {
            final String newValue = command.stringValueOfParameterNamed(lastnameParamName);
            actualChanges.put(lastnameParamName, newValue);
            this.lastname = newValue;
        }

        final String emailParamName = "email";
        if (command.isChangeInStringParameterNamed(emailParamName, this.email)) {
            final String newValue = command.stringValueOfParameterNamed(emailParamName);
            actualChanges.put(emailParamName, newValue);
            this.email = newValue;
        }

        return actualChanges;
    }

    private String[] getRolesAsIdStringArray() {
        List<String> roleIds = new ArrayList<String>();

        for (Role role : this.roles) {
            roleIds.add(role.getId().toString());
        }

        return roleIds.toArray(new String[roleIds.size()]);
    }

    /**
     * Delete is a <i>soft delete</i>. Updates flag so it wont appear in
     * query/report results.
     * 
     * Any fields with unique constraints and prepended with id of record.
     */
    public void delete() {
        this.deleted = true;
        this.enabled = false;
        this.accountNonExpired = false;
        this.firstTimeLoginRemaining = true;
        this.username = this.getId() + "_DELETED_" + this.username;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return this.populateGrantedAuthorities();
    }

    private List<GrantedAuthority> populateGrantedAuthorities() {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<GrantedAuthority>();
        for (Role role : this.roles) {
            Collection<Permission> permissions = role.getPermissions();
            for (Permission permission : permissions) {
                grantedAuthorities.add(new SimpleGrantedAuthority(permission.getCode()));
            }
        }
        return grantedAuthorities;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return this.accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return this.credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getEmail() {
        return this.email;
    }

    public Set<Role> getRoles() {
        return this.roles;
    }

    public Office getOffice() {
        return this.office;
    }

    public boolean canNotApproveLoanInPast() {
        return hasNotPermissionForAnyOf("ALL_FUNCTIONS", "APPROVEINPAST_LOAN", "PORTFOLIO_MANAGEMENT_SUPER_USER");
    }

    public boolean canNotRejectLoanInPast() {
        return hasNotPermissionForAnyOf("ALL_FUNCTIONS", "REJECTINPAST_LOAN", "PORTFOLIO_MANAGEMENT_SUPER_USER");
    }

    public boolean canNotWithdrawByClientLoanInPast() {
        return hasNotPermissionForAnyOf("ALL_FUNCTIONS", "WITHDRAWINPAST_LOAN", "PORTFOLIO_MANAGEMENT_SUPER_USER");
    }

    public boolean canNotDisburseLoanInPast() {
        return hasNotPermissionForAnyOf("ALL_FUNCTIONS", "DISBURSEINPAST_LOAN", "PORTFOLIO_MANAGEMENT_SUPER_USER");
    }

    public boolean canNotMakeRepaymentOnLoanInPast() {
        return hasNotPermissionForAnyOf("ALL_FUNCTIONS", "REPAYMENTINPAST_LOAN", "PORTFOLIO_MANAGEMENT_SUPER_USER");
    }

    public boolean hasNotPermissionForReport(String reportName) {

        if (hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", "REPORTING_SUPER_USER", "READ_" + reportName)) return true;

        return false;
    }

    public boolean hasNotPermissionForDatatable(String datatable, String accessType) {

        String matchPermission = accessType + "_" + datatable;

        if (accessType.equalsIgnoreCase("READ")) {

            if (hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", matchPermission)) return true;

            return false;
        }

        if (hasNotPermissionForAnyOf("ALL_FUNCTIONS", matchPermission)) return true;

        return false;
    }

    public boolean hasNotPermissionForAnyOf(final String... permissionCodes) {
        boolean hasNotPermission = true;
        for (String permissionCode : permissionCodes) {
            boolean checkPermission = this.hasPermissionTo(permissionCode);
            if (checkPermission) {
                hasNotPermission = false;
                break;
            }
        }
        return hasNotPermission;
    }

    public void validateHasReadPermission(final String entityType) {

        String authorizationMessage = "User has no authority to view " + entityType.toLowerCase() + "s";
        String matchPermission = "READ_" + entityType.toUpperCase();

        if (!(hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", matchPermission))) return;

        String higherPermission = "";
        if (entityType.equalsIgnoreCase("CHARGE")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("CLIENT")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("CLIENTNOTE")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("CLIENTIDENTIFIER")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("CLIENTIMAGE")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("CODE")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("CURRENCY")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("DOCUMENTMAN")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("FUND")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("GROUP")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("LOANPRODUCT")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("LOAN")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("OFFICE")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("OFFICETRANSACTION")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("PERMISSION")) {
            higherPermission = "USER_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("ROLE")) {
            higherPermission = "USER_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("USER")) {
            higherPermission = "USER_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("STAFF")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("SAVINGSPRODUCT")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("DEPOSITPRODUCT")) {
            higherPermission = "ORGANISATION_ADMINISTRATION_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("DEPOSITACCOUNT")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        } else if (entityType.equalsIgnoreCase("SAVINGSACCOUNT")) {
            higherPermission = "PORTFOLIO_MANAGEMENT_SUPER_USER";
        }

        if (!(higherPermission.equals(""))) {
            if (hasNotPermissionForAnyOf(higherPermission)) throw new NoAuthorizationException(authorizationMessage);
        }
    }

    private boolean hasPermissionTo(final String permissionCode) {
        boolean match = false;
        for (Role role : this.roles) {
            if (role.hasPermissionTo(permissionCode)) {
                match = true;
                break;
            }
        }
        return match;
    }

    public boolean hasIdOf(final Long userId) {
        return getId().equals(userId);
    }

    private boolean hasNotAnyPermission(final List<String> permissions) {
        return !hasAnyPermission(permissions);
    }

    private boolean hasAnyPermission(final List<String> permissions) {
        boolean hasAtLeastOneOf = false;

        for (final String permissionCode : permissions) {
            if (hasPermissionTo(permissionCode)) {
                hasAtLeastOneOf = true;
                break;
            }
        }

        return hasAtLeastOneOf;
    }

    public void validateHasPermissionTo(final String function, final List<String> allowedPermissions) {
        if (hasNotAnyPermission(allowedPermissions)) {
            final String authorizationMessage = "User has no authority to: " + function;
            throw new NoAuthorizationException(authorizationMessage);
        }
    }
}
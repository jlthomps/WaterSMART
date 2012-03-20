package gov.usgs.cida.watersmart.ldap;

import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.watersmart.parse.DSGParser;
import gov.usgs.cida.watersmart.util.JNDISingleton;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class LDAPConnect {

    private static final Logger LOG = LoggerFactory.getLogger(DSGParser.class);
    private static DynamicReadOnlyProperties jndiProps = JNDISingleton.getInstance();

    public static User authenticate(String username, String password) {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY,
                  "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(Context.PROVIDER_URL, jndiProps.getProperty(
                "watersmart.ldap.url", "ldaps://gsvaresh02.er.usgs.gov:636"));
        props.put(Context.REFERRAL, "ignore");

        // set properties for authentication
        props.put(Context.SECURITY_PRINCIPAL, username);
        props.put(Context.SECURITY_CREDENTIALS, password);
        
        User user = null;

        try {
            InitialDirContext context = new InitialDirContext(props);
            SearchControls ctrls = new SearchControls();
            ctrls.setReturningAttributes(new String[] { "dn", "mail", "givenname", "sn", "uid" });
            ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            
            NamingEnumeration<SearchResult> answers = context.search(
                    "OU=USGS,O=DOI",
                    "(uid=" + username + ")",
                    ctrls
                    );
            if (answers.hasMore()) {
                SearchResult result = answers.next();
                Attributes attributes = result.getAttributes();
                String mail = (String)attributes.get("mail").get();
                String givenname = (String)attributes.get("givenname").get();
                String sn = (String)attributes.get("sn").get();
                String uid = (String)attributes.get("uid").get();
                String dn = result.getNameInNamespace();
                
                user = new User(dn, mail, givenname, sn, uid);
                String group = jndiProps.getProperty("watersmart.ldap.group");
                answers = context.search(
                        "", 
                        "(&(objectClass=groupOfNames)(cn=" + group + ")(member=" + dn + "))",
                        null);
                if (answers.hasMore()) {
                    user.setAuthentication(true);
                }
            }
        }
        catch (NamingException ex) {
            LOG.debug("unable to authenticate user", ex);
        }
        finally {
            return user;
        }
    }
}

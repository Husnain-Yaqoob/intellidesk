package ie.intellidesk.config;

import ie.intellidesk.domain.*;
import ie.intellidesk.repo.IncidentRepository;
import ie.intellidesk.repo.IncidentUpdateRepository;
import ie.intellidesk.repo.TeamRepository;
import ie.intellidesk.repo.UserRepository;
import ie.intellidesk.service.IncidentService;
import ie.intellidesk.triage.PriorityMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds teams, two demo logins and a back catalogue of resolved incidents.
 *
 * <p>The resolved incidents are not decoration. They are the corpus the ML service
 * trains on in Milestone 2 — without a body of past incidents that carry real
 * resolution text, similarity search and resolution suggestion have nothing to work
 * from. Everything here is synthetic and says so.
 */
@Configuration
@ConditionalOnProperty(name = "intellidesk.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "password123";

    private record Template(String category, String subcategory, String title,
                            String description, String resolution, double baseHours) {
    }

    private static final List<Template> TEMPLATES = List.of(
            new Template("Network", "VPN", "VPN disconnecting repeatedly",
                    "My VPN drops every ten minutes when I work from home. I have to reconnect constantly and it logs me out of the shared drives.",
                    "Updated the VPN client to the current build and reissued the user certificate. Connection stable after reconnect.", 5.5),
            new Template("Network", "VPN", "Cannot connect to VPN from home",
                    "The VPN client says authentication failed even though my password works for everything else.",
                    "Reset the network credentials and cleared the cached profile. User able to authenticate afterwards.", 4.0),
            new Template("Network", "Wi-Fi", "Wi-Fi keeps dropping in the meeting rooms",
                    "Wi-Fi in the first floor meeting rooms disconnects during calls. Wired connections are fine.",
                    "Access point on the first floor had failed over to a congested channel. Rebooted the AP and pinned the channel.", 6.0),
            new Template("Network", "Connectivity", "Shared drive unreachable",
                    "I cannot reach the shared drive from my desk. Other people on the same floor can.",
                    "Switch port had negotiated at 10Mbps and was dropping frames. Moved the user to a spare port.", 3.5),

            new Template("Software", "Outlook", "Outlook crashes when opening attachments",
                    "Outlook closes itself whenever I open a PDF attachment. It happens with every PDF, not just one sender.",
                    "Disabled the conflicting PDF preview add-in and repaired the Office installation.", 4.5),
            new Template("Software", "Office", "Excel freezing on large files",
                    "Excel stops responding for minutes at a time when I open the monthly report workbook.",
                    "Workbook contained volatile array formulas over a full column. Rebuilt the ranges and enabled manual calculation.", 7.0),
            new Template("Software", "Installation", "Cannot install the finance application",
                    "The installer for the finance package fails part way through with an error about permissions.",
                    "Pushed the package through the software centre with elevated rights rather than a user-run installer.", 3.0),
            new Template("Software", "Teams", "Teams audio not working on calls",
                    "Nobody can hear me on Teams calls. The headset works in other applications.",
                    "Teams was bound to the wrong capture device after a driver update. Reset the device preferences.", 2.5),

            new Template("Hardware", "Laptop", "Laptop will not power on",
                    "My laptop is completely dead this morning. No lights, no fan, nothing on the screen.",
                    "Failed power adapter. Replaced the adapter and confirmed the battery charges.", 8.0),
            new Template("Hardware", "Printer", "Printer jamming on every job",
                    "The printer on the second floor jams on every print job, even single pages.",
                    "Worn pickup roller replaced and paper path cleared.", 6.5),
            new Template("Hardware", "Monitor", "Second monitor not detected",
                    "My second monitor stopped being detected after the last restart.",
                    "Docking station firmware was out of date. Updated firmware and reseated the display cable.", 4.0),
            new Template("Hardware", "Peripherals", "Keyboard keys not responding",
                    "Several keys on my keyboard have stopped working. Cleaning has not helped.",
                    "Replaced the keyboard from stock.", 2.0),

            new Template("Email", "Delivery", "Not receiving external emails",
                    "I have not received any emails from outside the company since yesterday afternoon.",
                    "Messages were being held by the mail filter after a rule change. Released the queue and corrected the rule.", 5.0),
            new Template("Email", "Mailbox", "Mailbox full and cannot send",
                    "I get an error that my mailbox is full whenever I try to send anything.",
                    "Increased the mailbox quota and archived mail older than two years.", 3.0),
            new Template("Email", "Spam", "Legitimate emails going to junk",
                    "Emails from one of our suppliers always land in my junk folder.",
                    "Added the supplier domain to the allow list after verifying SPF alignment.", 2.5),

            new Template("Account / Access", "Password", "Account locked out",
                    "My account is locked and I cannot log in to anything.",
                    "Unlocked the account and reset the password. Stale credentials on the user's phone were causing repeated lockouts.", 1.5),
            new Template("Account / Access", "Permissions", "No access to the finance shared folder",
                    "I have moved to the finance team but I cannot open their shared folder.",
                    "Added the user to the finance security group. Access confirmed after sign-out and sign-in.", 4.0),
            new Template("Account / Access", "Starter", "New starter needs system access",
                    "New starter begins on Monday and needs the standard account set and system access.",
                    "Provisioned the account, mailbox and standard group memberships from the starter template.", 12.0),
            new Template("Account / Access", "MFA", "Multi-factor prompts not arriving",
                    "I am not getting the authentication prompt on my phone so I cannot sign in.",
                    "Re-registered the authenticator app against the user's account.", 2.0),

            new Template("Security", "Phishing", "Suspicious email reported",
                    "I received an email asking me to confirm my password on a link. I did not click it but it looks convincing.",
                    "Confirmed as phishing. Blocked the sender domain, purged matching messages from all mailboxes and briefed the floor.", 3.5),
            new Template("Security", "Malware", "Antivirus alert on my machine",
                    "My machine popped up an antivirus warning about a blocked file this morning.",
                    "Threat was quarantined on arrival. Ran a full scan, confirmed clean, and removed the source download.", 6.0),
            new Template("Security", "Access", "Former employee account still active",
                    "A colleague who left last month still appears in the address book.",
                    "Disabled the account, revoked sessions and moved the mailbox to shared with the manager delegated.", 5.0)
    );

    @Bean
    ApplicationRunner seed(UserRepository users, TeamRepository teams,
                           IncidentRepository incidents, IncidentUpdateRepository updates,
                           PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) {
                log.info("Demo data already present — skipping seed");
                return;
            }

            List<Team> created = teams.saveAll(List.of(
                    new Team("Network Support", "Network"),
                    new Team("Desktop Support", "Software"),
                    new Team("Hardware Support", "Hardware"),
                    new Team("Messaging Team", "Email"),
                    new Team("Identity & Access", "Account / Access"),
                    new Team("Security Operations", "Security")));

            AppUser agent = new AppUser("Aoife Nolan", "agent@intellidesk.ie",
                    encoder.encode(DEMO_PASSWORD), Role.AGENT);
            agent.setTeam(created.get(0));
            AppUser employee = new AppUser("Declan Byrne", "employee@intellidesk.ie",
                    encoder.encode(DEMO_PASSWORD), Role.EMPLOYEE);
            AppUser secondEmployee = new AppUser("Marta Kowalski", "marta@intellidesk.ie",
                    encoder.encode(DEMO_PASSWORD), Role.EMPLOYEE);
            users.saveAll(List.of(agent, employee, secondEmployee));

            Random random = new Random(42); // fixed seed: the demo looks the same every time
            List<AppUser> raisers = List.of(employee, secondEmployee);
            List<Incident> batch = new ArrayList<>();

            for (int i = 0; i < 140; i++) {
                Template t = TEMPLATES.get(random.nextInt(TEMPLATES.size()));
                Impact impact = pick(random, Impact.values());
                Urgency urgency = pick(random, Urgency.values());
                Priority priority = PriorityMatrix.resolve(impact, urgency);

                Incident incident = new Incident(t.title(), t.description(), impact, urgency,
                        priority, raisers.get(random.nextInt(raisers.size())));
                incident.setCategory(t.category());
                incident.setSubcategory(t.subcategory());
                incident.setCategoryConfidence(0.70 + random.nextDouble() * 0.29);

                Instant createdAt = Instant.now()
                        .minus(random.nextInt(60), ChronoUnit.DAYS)
                        .minus(random.nextInt(24), ChronoUnit.HOURS);
                incident.setCreatedAt(createdAt);

                teams.findByHandlesCategoryIgnoreCase(t.category()).ifPresent(incident::setAssignedTeam);

                // ~80% resolved, so the dashboard has both a backlog and a history.
                if (random.nextDouble() < 0.8) {
                    double hours = Math.max(0.5, t.baseHours() * (0.5 + random.nextDouble()));
                    Instant resolvedAt = createdAt.plus((long) (hours * 60), ChronoUnit.MINUTES);
                    if (resolvedAt.isBefore(Instant.now())) {
                        incident.setStatus(IncidentStatus.RESOLVED);
                        incident.setResolvedAt(resolvedAt);
                        incident.setResolvedBy(agent);
                        incident.setResolutionText(t.resolution());
                    } else {
                        incident.setStatus(IncidentStatus.IN_PROGRESS);
                    }
                } else {
                    incident.setStatus(random.nextBoolean() ? IncidentStatus.ASSIGNED : IncidentStatus.IN_PROGRESS);
                }

                batch.add(incident);
            }

            incidents.saveAll(batch);
            batch.forEach(incident -> incident.setReference(IncidentService.reference(incident.getId())));
            incidents.saveAll(batch);

            log.info("Seeded {} demo incidents, {} teams and 3 demo users (password: {})",
                    batch.size(), created.size(), DEMO_PASSWORD);
        };
    }

    private static <T> T pick(Random random, T[] values) {
        return values[random.nextInt(values.length)];
    }
}

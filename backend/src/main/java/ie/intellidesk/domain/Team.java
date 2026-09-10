package ie.intellidesk.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** The ML category this team picks up by default, e.g. "Network". */
    @Column(name = "handles_category")
    private String handlesCategory;

    protected Team() {
    }

    public Team(String name, String handlesCategory) {
        this.name = name;
        this.handlesCategory = handlesCategory;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getHandlesCategory() { return handlesCategory; }
}

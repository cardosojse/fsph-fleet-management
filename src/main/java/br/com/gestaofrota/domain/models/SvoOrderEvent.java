package br.com.gestaofrota.domain.models;
import br.com.gestaofrota.domain.enums.SvoOrderStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="svo_order_events")
public class SvoOrderEvent {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false,updatable=false) private SvoCollectionOrder order;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id",nullable=false,updatable=false) private User author;
    @Column(name="author_name",nullable=false,updatable=false) private String authorName;
    @Column(nullable=false,updatable=false) private String type;
    @Column(length=2000,updatable=false) private String text;
    @Enumerated(EnumType.STRING) @Column(name="from_status",updatable=false) private SvoOrderStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name="to_status",nullable=false,updatable=false) private SvoOrderStatus toStatus;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    @Column(name="order_version",nullable=false,updatable=false) private long orderVersion;
    protected SvoOrderEvent(){}
    public SvoOrderEvent(SvoCollectionOrder order, User author, String type, String text, SvoOrderStatus from, Instant at) {
        this.order=order;this.author=author;this.authorName=author.getName();this.type=type;this.text=text;
        this.fromStatus=from;this.toStatus=order.getStatus();this.occurredAt=at;this.orderVersion=order.getVersion();
    }
    public UUID getId(){return id;} public User getAuthor(){return author;} public String getAuthorName(){return authorName;}
    public String getType(){return type;} public String getText(){return text;} public SvoOrderStatus getFromStatus(){return fromStatus;}
    public SvoOrderStatus getToStatus(){return toStatus;} public Instant getOccurredAt(){return occurredAt;} public long getOrderVersion(){return orderVersion;}
}

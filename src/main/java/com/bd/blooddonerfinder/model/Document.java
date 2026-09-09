@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private String originalFileName;

    private String objectKey;

    private String contentType;

    private Long fileSize;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
package br.edu.ufersa.cc.seg.auth.entities;

import java.util.UUID;

import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(name = "instances")
public class Instance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String identifier;

    @Column(nullable = false)
    private String secret;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InstanceType type;

}

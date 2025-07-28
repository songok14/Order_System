package be16.ordersystem.member.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Where;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Entity
// jpql을 제외하고 모든 조회 쿼리에 where del_yn = 'N'을 붙이는 효과
@SQLRestriction("del_yn = 'N'")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false)
    @Builder.Default
    private String delYn = "N";
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    public void deleteMember(String delYn){
        this.delYn = delYn;
    }
}

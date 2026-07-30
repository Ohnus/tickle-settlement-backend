package com.settlement.tickle.domain.member.service;

import com.settlement.tickle.domain.member.dto.request.MemberExistsRequestDto;
import com.settlement.tickle.domain.member.dto.request.MemberSignupRequestDto;
import com.settlement.tickle.domain.member.dto.response.MemberInfoResponseDto;
import com.settlement.tickle.domain.member.entity.Member;
import com.settlement.tickle.domain.member.entity.MemberRoleType;
import com.settlement.tickle.domain.member.repository.MemberRepository;
import com.settlement.tickle.global.auth.custom.CustomUserDetails;
import com.settlement.tickle.global.exception.BusinessException;
import com.settlement.tickle.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입을 위한 이메일 중복 여부
    public boolean existsByEmail(MemberExistsRequestDto existsRequestDto) {
        String email = existsRequestDto.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return memberRepository.existsByEmail(email);
    }

    // 회원가입을 위한 닉네임 중복 여부
    public boolean existsByNickname(MemberExistsRequestDto existsRequestDto) {
        String nickname = existsRequestDto.getNickname();
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return memberRepository.existsByNickname(nickname);
    }

    // 회원가입 (구매자/판매자 공개 가입 — 관리자는 더미 데이터로만 생성, 공개 가입으로는 발급하지 않음)
    @Transactional
    public void signup(MemberSignupRequestDto signupRequestDto) {

        if (memberRepository.existsByEmail(signupRequestDto.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (memberRepository.existsByNickname(signupRequestDto.getNickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        MemberRoleType role = signupRequestDto.getRole();
        if (role != MemberRoleType.MEMBER && role != MemberRoleType.HOST) {
            throw new BusinessException(ErrorCode.INVALID_SIGNUP_ROLE);
        }

        boolean isHost = role == MemberRoleType.HOST;
        if (isHost) {
            validateHostBizInfo(signupRequestDto);
        }

        Member member = Member.builder()
                .email(signupRequestDto.getEmail())
                .password(passwordEncoder.encode(signupRequestDto.getPassword()))
                .nickname(signupRequestDto.getNickname())
                .role(role)
                .hostBizNumber(isHost ? signupRequestDto.getHostBizNumber() : null)
                .hostBizName(isHost ? signupRequestDto.getHostBizName() : null)
                .hostBizBank(isHost ? signupRequestDto.getHostBizBank() : null)
                .hostBizDepositor(isHost ? signupRequestDto.getHostBizDepositor() : null)
                .hostBizBankNumber(isHost ? signupRequestDto.getHostBizBankNumber() : null)
                .build();

        memberRepository.save(member);
    }

    // 판매자 정산 지급 정보는 부분 누락 시 이후 정산 지급 자체가 불가능해지므로 가입 시점에 전부 필수로 검증
    private void validateHostBizInfo(MemberSignupRequestDto signupRequestDto) {
        boolean hasAllBizInfo = StringUtils.hasText(signupRequestDto.getHostBizNumber())
                && StringUtils.hasText(signupRequestDto.getHostBizName())
                && StringUtils.hasText(signupRequestDto.getHostBizBank())
                && StringUtils.hasText(signupRequestDto.getHostBizDepositor())
                && StringUtils.hasText(signupRequestDto.getHostBizBankNumber());

        if (!hasAllBizInfo) {
            throw new BusinessException(ErrorCode.HOST_BIZ_INFO_REQUIRED);
        }
    }

    // 로그인
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return new CustomUserDetails(member);
    }

    // 내 정보 조회 (판매자 대시보드 등에서 사용)
    public MemberInfoResponseDto getMyInfo(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        return MemberInfoResponseDto.from(member);
    }

    // 유저 정보 수정

    // 회원탈퇴(소프트 딜리트) + Redis Refresh Token 삭제
}

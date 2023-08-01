package com.ssafy.tingbackend.user.service;

import com.ssafy.tingbackend.common.exception.CommonException;
import com.ssafy.tingbackend.common.exception.ExceptionType;
import com.ssafy.tingbackend.common.security.JwtAuthenticationProvider;
import com.ssafy.tingbackend.common.security.JwtUtil;
import com.ssafy.tingbackend.entity.type.SidoType;
import com.ssafy.tingbackend.entity.user.*;
import com.ssafy.tingbackend.user.dto.*;
import com.ssafy.tingbackend.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final LoginLogRepository loginLogRepository;
    private final AdditionalInfoRepository additionalInfoRepository;
    private final UserHobbyRepository userHobbyRepository;
    private final UserPersonalityRepository userPersonalityRepository;
    private final UserStyleRepository userStyleRepository;
    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;
    private final EmailRepository emailRepository;
    private final PhoneNumberAuthRepository phoneNumberRepository;

    private final SmsService smsService;

    @Value("${file.path}")
    private String uploadPath;

    public Map<String, String> login(UserDto.Basic userDto) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userDto.getEmail(), userDto.getPassword());

        Authentication authenticate = jwtAuthenticationProvider.authenticate(authenticationToken);
        User principal = (User) authenticate.getPrincipal();

        String accessToken = JwtUtil.generateAccessToken(String.valueOf(principal.getId()));
        String refreshToken = JwtUtil.generateRefreshToken(String.valueOf(principal.getId()));

        Map<String, String> result = new HashMap<>();
        result.put("access-token", accessToken);
        result.put("refresh-token", refreshToken);

        // 로그인 기록 저장
        log.info("{} 유저 로그인 시도", userDto.getEmail());
        loginLogRepository.save(LoginLog.builder()
                .user(principal)
                .build());

        return result;
    }

    public Map<String, String> refreshToken(String userId) {
        String accessToken = JwtUtil.generateAccessToken(String.valueOf(userId));
        String refreshToken = JwtUtil.generateRefreshToken(String.valueOf(userId));

        Map<String, String> result = new HashMap<>();
        result.put("access-token", accessToken);
        result.put("refresh-token", refreshToken);

        // 로그인 기록 저장
        return result;
    }

    public void signUp(UserDto.Signup userDto) {
        userDto.encodePassword(passwordEncoder.encode(userDto.getPassword()));  // 비밀번호 암호화

        // 기본 정보 UserDto -> User 변환
        ModelMapper modelMapper = new ModelMapper();
        User user = modelMapper.map(userDto, User.class);

        // 지역 정보 enum 타입으로 변환
        user.setRegion(SidoType.getEnumType(userDto.getRegion()));

        // mbti, 음주, 직업, 종교, 흡연 AdditionalInfo 객체로 변환
        if (userDto.getMbtiCode() != null) user.setMbtiCode(getAdditionalInfo(userDto.getMbtiCode()));
        if (userDto.getDrinkingCode() != null) user.setDrinkingCode(getAdditionalInfo(userDto.getDrinkingCode()));
        if (userDto.getJobCode() != null) user.setJobCode(getAdditionalInfo(userDto.getJobCode()));
        if (userDto.getReligionCode() != null) user.setReligionCode(getAdditionalInfo(userDto.getReligionCode()));
        if (userDto.getSmokingCode() != null) user.setSmokingCode(getAdditionalInfo(userDto.getSmokingCode()));

        userRepository.save(user); // DB에 저장

        // 취미, 성격, 선호 스타일 각 매핑 객체로 변환
        if (userDto.getHobbyCodeList().size() > 0) {
            ArrayList<UserHobby> userHobbies = new ArrayList<>();
            userDto.getHobbyCodeList().forEach(hobbyCode ->
                    userHobbies.add(new UserHobby(user, getAdditionalInfo(hobbyCode))));

            userHobbyRepository.saveAll(userHobbies); // DB에 저장
        }

        if (userDto.getPersonalityCodeList().size() > 0) {
            ArrayList<UserPersonality> userPersonalities = new ArrayList<>();
            userDto.getPersonalityCodeList().forEach(personalityCode ->
                    userPersonalities.add(new UserPersonality(user, getAdditionalInfo(personalityCode))));

            userPersonalityRepository.saveAll(userPersonalities); // DB에 저장
        }

        if (userDto.getStyleCodeList().size() > 0) {
            ArrayList<UserStyle> userStyles = new ArrayList<>();
            userDto.getStyleCodeList().forEach(styleCode ->
                    userStyles.add(new UserStyle(user, getAdditionalInfo(styleCode))));

            userStyleRepository.saveAll(userStyles); // DB에 저장
        }
    }

    public boolean checkNickname(String nickname) {
        return userRepository.isDuplicatedNickname(nickname);
    }

    private AdditionalInfo getAdditionalInfo(Long code) {
        return additionalInfoRepository.findById(code)
                .orElseThrow(() -> new CommonException(ExceptionType.ADDITOIONAL_INFO_NOT_FOUND));
    }

    @Transactional
    public UserDto.Detail userDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));

        List<AdditionalInfoDto> hobbyAdditional = new ArrayList<>();
        List<AdditionalInfoDto> styleAdditional = new ArrayList<>();
        List<AdditionalInfoDto> personalityAdditional = new ArrayList<>();

        user.getUserHobbys().forEach(hobby -> hobbyAdditional.add(AdditionalInfoDto.of(hobby.getAdditionalInfo())));
        user.getUserStyles().forEach(style -> styleAdditional.add(AdditionalInfoDto.of(style.getAdditionalInfo())));
        user.getUserPersonalities().forEach(personality -> personalityAdditional.add(AdditionalInfoDto.of(personality.getAdditionalInfo())));

        return UserDto.Detail.of(user, hobbyAdditional, styleAdditional, personalityAdditional);
    }

    @Transactional
    public void withdrawal(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));
        user.setRemoved(true);
        user.setRemovedTime(LocalDateTime.now());
    }

    public boolean checkUser(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));

        if (passwordEncoder.matches(password, user.getPassword())) return true;
        else return false;
    }

    @Transactional
    public void modifyPassword(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));
        user.setPassword(passwordEncoder.encode(password));
    }

    public String findEmail(UserDto.Basic userDto) {
        String name = userDto.getName();
        String phoneNumber = userDto.getPhoneNumber();

        // 이름, 전화번호가 중복되는 경우를 생각해야 할까..?
        return userRepository.findEmail(name, phoneNumber)
                .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));
    }

    public void sendEmail(String email) {
        long verifiedCode = Math.round(100000 + Math.random() * 899999);
        if (emailRepository.findByEmail(email).isPresent()) {
            EmailAuthDto emailAuthDto = emailRepository.findByEmail(email)
                    .orElseThrow(() -> new CommonException(ExceptionType.EMAIL_NOT_FOUND));
            emailRepository.delete(emailAuthDto);
        }
        insertCode(email, Long.toString(verifiedCode));
        // 이메일 발신될 데이터 적재
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setTo(email); // 수신자 바꾸기
        simpleMailMessage.setSubject("이메일 인증 코드입니다.");
        simpleMailMessage.setText("아래의 인증 코드를 입력해주세요. \n" +
                verifiedCode + " \n");

        // 이메일 발신
        javaMailSender.send(simpleMailMessage);
    }

    public void validateEmailCode(String email, String authCode) {
        EmailAuthDto emailAuthDto = emailRepository.findByEmail(email)
                .orElseThrow(() -> new CommonException(ExceptionType.EMAIL_NOT_FOUND));

        if (!emailAuthDto.getKey().equals(authCode)) {
            throw new CommonException(ExceptionType.EMAIL_CODE_NOT_MATCH);
        }
        emailRepository.delete(emailAuthDto);
    }

    public void insertCode(String email, String code) {
        EmailAuthDto emailAuthDto = new EmailAuthDto(email, code);
        emailRepository.save(emailAuthDto);
    }

    public boolean checkDuplicatedEmail(String email) {
        if (userRepository.findByEmail(email).isPresent()) return true;
        else return false;
    }

    public void authPhoneNumber(String phoneNumber) {
        // 인증번호 생성
        Long authCode = Math.round(1000 + Math.random() * 8999);

        // 문자 전송 - 과금 조심!
        String messageContent = "TING 전화번호 인증 코드입니다.\n" +
                "[" + authCode + "]";
        Long time = System.currentTimeMillis();
        try {
            SmsDto.Response response = smsService.sendSms(time, new MessageDto(phoneNumber, messageContent));
            if (!response.getStatusCode().equals("202")) throw new CommonException(ExceptionType.SMS_SEND_FAILED);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CommonException(ExceptionType.SMS_SEND_FAILED);
        }

        // 같은 전화번호로 이미 인증코드가 존재하는 경우 이전 코드 삭제
        Optional<PhoneNumberAuthDto> findPhoneNumberAuth = phoneNumberRepository.findByPhoneNumber(phoneNumber);
        if (findPhoneNumberAuth.isPresent()) {
            phoneNumberRepository.delete(findPhoneNumberAuth.get());
        }
        // 인증코드 몽고 DB에 저장
        insertPhoneAuthCode(phoneNumber, authCode.toString());
    }

    private void insertPhoneAuthCode(String phoneNumber, String authCode) {
        PhoneNumberAuthDto phoneNumberAuthDto = new PhoneNumberAuthDto(phoneNumber, authCode);
        phoneNumberRepository.save(phoneNumberAuthDto);
    }

    public void validatePhoneAuthCode(String phoneNumber, String authCode) {
        PhoneNumberAuthDto phoneNumberAuthDto = phoneNumberRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new CommonException(ExceptionType.PHONE_NUMBER_NOT_FOUND));

        if (!phoneNumberAuthDto.getAuthCode().equals(authCode)) {
            throw new CommonException(ExceptionType.PHONE_AUTH_CODE_NOT_MATCH);
        }
        phoneNumberRepository.delete(phoneNumberAuthDto);  // 인증 성공 시 DB에서 데이터 삭제
    }

    @Transactional
    public void modifyUser(Long userId, UserDto.Put userDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));

        log.info("{} 유저 정보 수정 시도", user.getEmail());

        user.setPhoneNumber(userDto.getPhoneNumber());
        user.setRegion(SidoType.getEnumType(userDto.getRegion()));
        user.setProfileImage(userDto.getProfileImage());
        user.setHeight(userDto.getHeight());
        user.setIntroduce(userDto.getIntroduce());
        user.setJobCode(getAdditionalInfo(userDto.getJobCode()));
        user.setDrinkingCode(getAdditionalInfo(userDto.getDrinkingCode()));
        user.setReligionCode(getAdditionalInfo(userDto.getReligionCode()));
        user.setMbtiCode(getAdditionalInfo(userDto.getMbtiCode()));
        user.setSmokingCode(getAdditionalInfo(userDto.getSmokingCode()));

        userHobbyRepository.deleteAll(user.getUserHobbys());
        userStyleRepository.deleteAll(user.getUserStyles());
        userPersonalityRepository.deleteAll(user.getUserPersonalities());

        ArrayList<UserHobby> userHobbies = new ArrayList<>();
        ArrayList<UserStyle> userStyles = new ArrayList<>();
        ArrayList<UserPersonality> userPersonalities = new ArrayList<>();

        userDto.getHobbyCodeList().forEach(hobbyCode -> userHobbies.add(UserHobby.builder()
                .user(user)
                .additionalInfo(getAdditionalInfo(hobbyCode))
                .build())
        );
        userDto.getStyleCodeList().forEach(styleCode -> userStyles.add(UserStyle.builder()
                .user(user)
                .additionalInfo(getAdditionalInfo(styleCode))
                .build())
        );
        userDto.getPersonalityCodeList().forEach(personalityCode -> userPersonalities.add(UserPersonality.builder()
                .user(user)
                .additionalInfo(getAdditionalInfo(personalityCode))
                .build())
        );

        userHobbyRepository.saveAll(userHobbies);
        userStyleRepository.saveAll(userStyles);
        userPersonalityRepository.saveAll(userPersonalities);
    }

    public void findPassword(UserDto.Basic userDto) {
        String name = userDto.getName();
        String phoneNumber = userDto.getPhoneNumber();
        String email = userDto.getEmail();

        User user = userRepository.findPassword(name, phoneNumber, email)
                .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));
        // 이메일 전송
        String password = createKey();
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("임시 비밀번호입니다.");
        simpleMailMessage.setText("아래의 비밀번호를 입력해주세요. \n" +
                password + " \n");
        javaMailSender.send(simpleMailMessage);
        user.setPassword(passwordEncoder.encode(password));
    }

    public static String createKey() {
        StringBuffer key = new StringBuffer();
        Random rnd = new Random();

        for (int i = 0; i < 8; i++) { // 인증코드 8자리
            int index = rnd.nextInt(3); // 0~2 까지 랜덤

            switch (index) {
                case 0:
                    key.append((char) ((int) (rnd.nextInt(26)) + 97));
                    //  a~z  (ex. 1+97=98 => (char)98 = 'b')
                    break;
                case 1:
                    // 특수문자
                    key.append((char) ((int) (rnd.nextInt(15) + 33)));
//                    key.append((char) ((int) (rnd.nextInt(26)) + 65));
                    //  A~Z
                    break;
                case 2:
                    key.append((rnd.nextInt(10)));
                    // 0~9
                    break;
            }
        }
        return key.toString();
    }


    @Transactional
    public void saveProfile(MultipartFile file, Principal principal) throws IOException {
        if (file == null) {
            throw new CommonException(ExceptionType.PROFILE_FILE_NOT_FOUND);
        }

        if (!file.isEmpty()) {
            String today = new SimpleDateFormat("yyMMdd").format(new Date());
            String saveFolder = uploadPath + File.separator + today;

            // 폴더가 없다면 저장경로의 폴더를 생성
            File folder = new File(saveFolder);
            if (!folder.exists()) folder.mkdirs();

            String originalFileName = file.getOriginalFilename();
            if (!originalFileName.isEmpty()) {
                // 파일 저장
                String saveFileName = UUID.randomUUID().toString()
                        + originalFileName.substring(originalFileName.lastIndexOf('.'));
                file.transferTo(new File(folder, saveFileName));

                // 프로필 세팅
                User user = userRepository.findById(Long.parseLong(principal.getName()))
                        .orElseThrow(() -> new CommonException(ExceptionType.USER_NOT_FOUND));
                user.setProfileImage(saveFolder + File.separator + today + File.separator + saveFileName);
                userRepository.save(user);
            }
        }

    }
}

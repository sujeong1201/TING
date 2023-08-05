import { useSelector } from "react-redux";

function MyInformation() {
  let userdata = useSelector((state) => state.userdataReducer.userdata);
  let regionList = [{regionEn: "SEOUL", regionKor: "서울"}, {regionEn: "DAEJEON", regionKor: "대전"},
  {regionEn: "BUSAN", regionKor: "부산"}, {regionEn: "SEOUL", regionKor: "대구"}, {regionEn: "SEOUL", regionKor: "인천"},
  {regionEn: "SEOUL", regionKor: "광주"}, {regionEn: "SEOUL", regionKor: "울산"},{regionEn: "SEOUL", regionKor: "경기"},
  {regionEn: "SEOUL", regionKor: "강원"}, {regionEn: "SEOUL", regionKor: "충복"}, {regionEn: "SEOUL", regionKor: "충남"},
  {regionEn: "SEOUL", regionKor: "세종"}, {regionEn: "SEOUL", regionKor: "전북"}, {regionEn: "SEOUL", regionKor: "전남"},
  {regionEn: "SEOUL", regionKor: "경북"}, {regionEn: "SEOUL", regionKor: "경남"}, {regionEn: "SEOUL", regionKor: "제주"},
]

  // 지역 영어를 한글로 변환
  const matchRegion = (regionData) => {
    const regionName = regionList.map((region) => region.regionEn === regionData && region.regionKor)
    return regionName
  };

  return (
    <div>
      <h2>내 정보 페이지</h2>
      <p>이름 : { userdata.name }</p>
      <p>성별 : { userdata.gender }</p>
      <p>이메일 : { userdata.email }</p>
      <p>전화번호 : { userdata.phoneNumber }</p>
      <p>생년월일 : { userdata.birth }</p>
      <p>지역 : { matchRegion(userdata.region) }</p>
      <br/>
      <p>닉네임 : { userdata.nickname }</p>
      <p>키 : { userdata.height }</p>
      <p>MBTI : { userdata.mbtiCode.name }</p>
      <p>주량 : { userdata.drinkingCode.name }</p>
      <p>흡연 : { userdata.smokingCode.name }</p>
      <p>종교 : { userdata.religionCode.name }</p>
      <p>직업 : { userdata.jobCode.name }</p>
      <br/>
      <p>취미 : { userdata.userHobbys.map((hobby) => hobby.name) }</p>
      <br/>
      <p>성격 : { userdata.userPersonalities.map((personalities) => personalities.name) }</p>
    </div>
  )
}

export default MyInformation
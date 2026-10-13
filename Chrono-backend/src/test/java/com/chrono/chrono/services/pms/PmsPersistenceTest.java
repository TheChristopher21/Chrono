package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.Room;
import com.chrono.chrono.entities.pms.RoomType;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.RoomRepository;
import com.chrono.chrono.repositories.pms.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
class PmsPersistenceTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private HotelPropertyRepository propertyRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void persistsACompanyScopedHotelInventoryAndKeepsOtherCompaniesIsolated() {
        Company company = companyRepository.save(new Company("Chrono Hotel AG"));
        Company otherCompany = companyRepository.save(new Company("Other Hotels AG"));

        HotelProperty property = new HotelProperty();
        property.setCompany(company);
        property.setCode("ZRH");
        property.setName("Chrono Zürich");
        property = propertyRepository.save(property);

        RoomType roomType = new RoomType();
        roomType.setProperty(property);
        roomType.setCode("DBL");
        roomType.setName("Doppelzimmer");
        roomType = roomTypeRepository.save(roomType);

        Room room = new Room();
        room.setProperty(property);
        room.setRoomType(roomType);
        room.setNumber("101");
        roomRepository.save(room);

        assertThat(propertyRepository.findAllByCompany_IdOrderByNameAsc(company.getId()))
                .extracting(HotelProperty::getCode)
                .containsExactly("ZRH");
        assertThat(roomTypeRepository.findAllByProperty_IdOrderBySortOrderAscNameAsc(property.getId()))
                .extracting(RoomType::getCode)
                .containsExactly("DBL");
        assertThat(roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(property.getId()))
                .extracting(Room::getNumber)
                .containsExactly("101");
        assertThat(propertyRepository.findAllByCompany_IdOrderByNameAsc(otherCompany.getId())).isEmpty();
        assertThat(roomRepository.findByIdAndProperty_Company_Id(room.getId(), otherCompany.getId())).isEmpty();
    }
}

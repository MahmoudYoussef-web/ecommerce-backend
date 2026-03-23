package com.mahmoud.ecommerce_backend.service.address;

import com.mahmoud.ecommerce_backend.dto.address.AddressResponse;
import com.mahmoud.ecommerce_backend.dto.address.CreateAddressRequest;
import com.mahmoud.ecommerce_backend.entity.Address;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.AddressMapper;
import com.mahmoud.ecommerce_backend.repository.AddressRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AddressMapper addressMapper;

    @Override
    public AddressResponse createAddress(CreateAddressRequest request) {

        User user = getCurrentUser();

        Address address = Address.builder()
                .user(user)
                .country(request.getCountry())
                .city(request.getCity())
                .addressLine1(request.getStreet())
                .postalCode(request.getZipCode())
                .build();

        Address saved = addressRepository.save(address);

        return addressMapper.toResponse(saved);
    }

    @Override
    public List<AddressResponse> getUserAddresses() {

        User user = getCurrentUser();

        return addressRepository.findByUserId(user.getId())
                .stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    public void deleteAddress(Long id) {

        User user = getCurrentUser();

        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You are not allowed to delete this address");
        }

        addressRepository.delete(address);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
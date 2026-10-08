package com.example.ecommerce.customer.service;

import com.example.ecommerce.customer.dto.CustomerCreationRequest;
import com.example.ecommerce.customer.dto.CustomerUpdateRequest;
import com.example.ecommerce.customer.dto.CustomerResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.customer.mapper.CustomerMapper;
import com.example.ecommerce.customer.repository.CustomerRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerService {
    final CustomerRepository customerRepository;
    final CustomerMapper customerMapper;

    public CustomerResponse createCustomer(CustomerCreationRequest customerCreationRequest){
        if(customerRepository.existsByEmail(customerCreationRequest.getEmail())){
            throw new DuplicateResourceException("Email đã tồn tại trên hệ thống!");
        }
        if(customerCreationRequest.getPhone() != null && customerRepository.existsByPhone(customerCreationRequest.getPhone())){
            throw new DuplicateResourceException("Số điện thoại này đã được sử dụng trên hệ thống!");
        }

        Customer customer = customerMapper.createCustomer(customerCreationRequest);

        return customerMapper.toResponse(customerRepository.save(customer));

    }

//    public List<CustomerResponse> getCustomer(){
//        return customerRepository.findAll().stream()
//                .map(customerMapper::toResponse)
//                .toList();
//    }

    public PageResponse<CustomerResponse> getAllCustomer(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        Page<Customer> customerPage = customerRepository.findAll(pageable);
        Page<CustomerResponse> customerResponses = customerPage.map(customerMapper::toResponse);
        return PageResponse.of(customerResponses);
    }

    public CustomerResponse getCustomerById(int id){
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã ID: " + id));

        return customerMapper.toResponse(customer);
    }

    public CustomerResponse updateCustomer(int id, CustomerUpdateRequest customerCreationRequest){
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã ID: " + id));

        if(!customer.getEmail().equals(customerCreationRequest.getEmail())
        && customerRepository.existsByEmail(customerCreationRequest.getEmail())){
            throw new DuplicateResourceException("Email đã tồn tại trên hệ thống");
        }

        customerMapper.updateCustomer(customerCreationRequest, customer);

        return customerMapper.toResponse(customerRepository.save(customer));

    }

    public void deteleCustomer(int id){
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã ID: " + id));

        if(!customer.getOrders().isEmpty()){
            throw new ConflictException("Không thể xóa khách hàng đã có đơn hàng trong hệ thống");
        }

        customerRepository.delete(customer);
    }

}

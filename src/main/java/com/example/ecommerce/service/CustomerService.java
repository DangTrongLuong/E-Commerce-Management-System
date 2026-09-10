package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.mapper.CustomerMapper;
import com.example.ecommerce.repository.CustomerRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            throw new RuntimeException("Email đã tồn tại !");
        }

        Customer customer = customerMapper.createCustomer(customerCreationRequest);

        return customerMapper.toResponse(customerRepository.save(customer));

    }

    public List<CustomerResponse> getCustomer(){
        return customerRepository.findAll().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    public CustomerResponse getCustomerById(int id){
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng !"));

        return customerMapper.toResponse(customer);
    }

    public CustomerResponse updateCustomer(int id, CustomerCreationRequest customerCreationRequest){
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khác hàng !"));

        if(!customer.getEmail().equals(customerCreationRequest.getEmail())
        && customerRepository.existsByEmail(customerCreationRequest.getEmail())){
            throw new RuntimeException("Email đã tồn tại ");
        }

        customerMapper.updateCustomer(customerCreationRequest, customer);

        return customerMapper.toResponse(customerRepository.save(customer));

    }

    public void deteleCustomer(int id){
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));

        customerRepository.delete(customer);
    }

}

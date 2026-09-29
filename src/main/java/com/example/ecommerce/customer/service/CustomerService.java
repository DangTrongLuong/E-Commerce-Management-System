package com.example.ecommerce.customer.service;

import com.example.ecommerce.customer.dto.CustomerCreationRequest;
import com.example.ecommerce.customer.dto.CustomerUpdateRequest;
import com.example.ecommerce.customer.dto.CustomerResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.common.exception.ConflictException;
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
            throw new RuntimeException("Email đã tồn tại !");
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng !"));

        return customerMapper.toResponse(customer);
    }

    public CustomerResponse updateCustomer(int id, CustomerUpdateRequest customerCreationRequest){
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

        if(!customer.getOrders().isEmpty()){
            throw new ConflictException("Không thể xóa khách đàng đã có đơn hàng");
        }

        customerRepository.delete(customer);
    }

}

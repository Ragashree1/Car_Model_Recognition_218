# Car_Model_Recognition_218

This project focuses on training machine learning and deep learning models to classify car models using the **Stanford Cars Dataset**. We experimented with multiple models and also developed an **Android application** using Kotlin to deploy the trained models for real-world use.  

## Models Trained  
We trained the following models to predict car models:  
- **ResNet** (Residual Neural Network)  
- **SRGNN with ResNet** (Spatial Reasoning Graph Neural Network utilizing ResNet)
- **SRGNN with TResNet** (Spatial Reasoning Graph Neural Network combined with TResNet)  
- **SVM** (Support Vector Machine)  
- **VisionTransformerModel** (ViT: Transformer-based image classification)  
- **I2 HOF1**  
- **KNN** (K-Nearest Neighbors)  

## Android Application  
We developed an **Android application** using **Kotlin** to integrate the trained models. The app enables users to upload or capture car images and receive predictions based on the trained models.  

## Dataset  
We used the **Stanford Cars Dataset**, which contains **196 car models**. The dataset is labeled with car model, label, and bounding box annotations, making it well-suited for fine-grained image classification tasks.  

## Repository Structure  
- `/ModelNotebooks` - Jupyter notebooks for training and evaluation  
- `/apkSourceCode` - Source code for the Android application   

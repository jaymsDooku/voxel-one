#version 330 core
flat in float vTransmission;
void main(){if(vTransmission>.5)discard;}

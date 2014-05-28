function packetToWaveFile( file_name, samplerate )
%UNTITLED2 Summary of this function goes here
%   Detailed explanation goes here

    data = load(file_name)';
    
    output = strcat(file_name,'.wav');
    
    wavwrite(data, samplerate, output);


end


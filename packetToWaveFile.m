function packetToWaveFile( file_name )
%UNTITLED2 Summary of this function goes here
%   Detailed explanation goes here

    data = load(file_name)';
    
    output = strcat(file_name,'.wav');
    
    wavwrite(data, 48000, output);


end

